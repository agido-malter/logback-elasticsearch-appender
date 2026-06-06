package com.agido.logback.elasticsearch.writer;

import ch.qos.logback.core.ContextBase;
import com.agido.logback.elasticsearch.config.HttpRequestHeader;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;

public class ElasticsearchWriterHttpTest {

    private WireMockServer server;

    @Before
    public void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
    }

    @After
    public void tearDown() {
        server.stop();
    }

    private Settings settings() throws Exception {
        Settings settings = new Settings();
        settings.setUrl(new URL("http://localhost:" + server.port() + "/_bulk"));
        settings.setReadTimeout(5000);
        settings.setConnectTimeout(5000);
        return settings;
    }

    private ErrorReporter errorReporter(Settings settings) {
        return new ErrorReporter(settings, new ContextBase());
    }

    private HttpRequestHeaders headers(String name, String value) {
        HttpRequestHeaders headers = new HttpRequestHeaders();
        if (name != null) {
            HttpRequestHeader h = new HttpRequestHeader();
            h.setName(name);
            h.setValue(value);
            headers.addHeader(h);
        }
        return headers;
    }

    private void write(ElasticsearchWriter writer, String data) {
        writer.write(data.toCharArray(), 0, data.length());
    }

    @Test
    public void should_post_body_with_json_content_type_on_200() throws Exception {
        stubFor(post(urlEqualTo("/_bulk")).willReturn(aResponse().withStatus(200).withBody("ok")));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, "{\"index\":{}}\n{\"message\":\"hello\"}\n");
        writer.sendData();

        assertFalse(writer.hasPendingData());
        verify(postRequestedFor(urlEqualTo("/_bulk"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(containing("hello")));
    }

    @Test
    public void should_throw_and_clear_buffer_on_400() throws Exception {
        stubFor(post(urlEqualTo("/_bulk")).willReturn(aResponse().withStatus(400).withBody("bad request")));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, "{\"index\":{}}\n");

        try {
            writer.sendData();
            fail("Expected IOException on 400");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("400"));
            assertTrue(e.getMessage().contains("bad request"));
        }

        // 4xx clears the buffer
        assertFalse(writer.hasPendingData());
        assertEquals(0, writer.getSendBuffer().length());
    }

    @Test
    public void should_reuse_client_across_two_sends() throws Exception {
        stubFor(post(urlEqualTo("/_bulk")).willReturn(aResponse().withStatus(200)));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));

        write(writer, "first\n");
        writer.sendData();
        write(writer, "second\n");
        writer.sendData();

        verify(2, postRequestedFor(urlEqualTo("/_bulk")));
    }

    @Test
    public void should_gzip_body_when_content_encoding_gzip() throws Exception {
        stubFor(post(urlEqualTo("/_bulk")).willReturn(aResponse().withStatus(200)));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(
                errorReporter(settings), settings, headers("Content-Encoding", "gzip"));
        String payload = "{\"message\":\"gzipped-payload\"}";
        write(writer, payload);
        writer.sendData();

        List<com.github.tomakehurst.wiremock.verification.LoggedRequest> requests =
                findAll(postRequestedFor(urlEqualTo("/_bulk")));
        assertEquals(1, requests.size());

        // The Content-Encoding: gzip header must have been sent.
        verify(postRequestedFor(urlEqualTo("/_bulk"))
                .withHeader("Content-Encoding", equalTo("gzip")));

        byte[] received = requests.get(0).getBody();
        boolean gzipped = received.length >= 2
                && (received[0] & 0xff) == 0x1f && (received[1] & 0xff) == 0x8b;
        String body;
        if (gzipped) {
            // Raw wire bytes: gunzip independently to recover the payload.
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(received))) {
                body = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            // WireMock already transparently decompressed the gzip body.
            body = new String(received, StandardCharsets.UTF_8);
        }
        assertEquals(payload, body);
    }
}
