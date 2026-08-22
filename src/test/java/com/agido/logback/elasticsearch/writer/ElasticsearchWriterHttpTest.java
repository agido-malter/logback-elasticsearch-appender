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
    public void should_keep_only_retryable_events_after_partial_bulk_failure() throws Exception {
        String event1 = "{\"index\":{}}\n{\"message\":\"success\"}\n";
        String event2 = "{\"index\":{}}\n{\"message\":\"retry\"}\n";
        String event3 = "{\"index\":{}}\n{\"message\":\"invalid\"}\n";
        String response = "{\"errors\":true,\"items\":["
                + "{\"index\":{\"status\":201}},"
                + "{\"index\":{\"status\":429,\"error\":{\"type\":\"rejected\"}}},"
                + "{\"index\":{\"status\":400,\"error\":{\"type\":\"mapper_error\"}}}]}";
        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(1)
                .withRequestBody(containing("success"))
                .withRequestBody(containing("invalid"))
                .willReturn(aResponse().withStatus(200).withBody(response)));
        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody("{\"errors\":false,\"items\":[]}")));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, event1 + event2 + event3);

        try {
            writer.sendData();
            fail("Expected IOException for retryable Bulk API item failure");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("retained for retry"));
        }

        assertEquals(event2, writer.getSendBuffer().toString());

        writer.sendData();

        assertFalse(writer.hasPendingData());
        verify(2, postRequestedFor(urlEqualTo("/_bulk")));
        verify(postRequestedFor(urlEqualTo("/_bulk")).withRequestBody(equalTo(event2)));
    }

    @Test
    public void should_drop_permanent_item_failure_without_retrying_successful_events() throws Exception {
        String event1 = "{\"create\":{}}\n{\"message\":\"success\"}\n";
        String event2 = "{\"create\":{}}\n{\"message\":\"invalid\"}\n";
        String response = "{\"errors\":true,\"items\":["
                + "{\"create\":{\"status\":201}},"
                + "{\"create\":{\"status\":400,\"error\":{\"type\":\"mapper_error\"}}}]}";
        stubFor(post(urlEqualTo("/_bulk")).willReturn(aResponse().withStatus(200).withBody(response)));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, event1 + event2);
        writer.sendData();

        assertFalse(writer.hasPendingData());
    }

    @Test
    public void should_preserve_buffer_when_bulk_error_response_cannot_be_correlated() throws Exception {
        String events = "{\"index\":{}}\n{\"message\":\"one\"}\n"
                + "{\"index\":{}}\n{\"message\":\"two\"}\n";
        String response = "{\"errors\":true,\"items\":[{\"index\":{\"status\":429}}]}";
        stubFor(post(urlEqualTo("/_bulk")).willReturn(aResponse().withStatus(200).withBody(response)));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, events);

        assertThrows(IOException.class, writer::sendData);
        assertEquals(events, writer.getSendBuffer().toString());
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
    public void should_split_bulk_request_on_413_without_losing_events() throws Exception {
        String event1 = "{\"index\":{}}\n{\"message\":\"event-1\"}\n";
        String event2 = "{\"index\":{}}\n{\"message\":\"event-2\"}\n";
        String event3 = "{\"index\":{}}\n{\"message\":\"event-3\"}\n";
        String event4 = "{\"index\":{}}\n{\"message\":\"event-4\"}\n";

        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(1)
                .withRequestBody(containing("event-1"))
                .withRequestBody(containing("event-4"))
                .willReturn(aResponse().withStatus(413).withBody("payload too large")));
        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, event1 + event2 + event3 + event4);
        writer.sendData();

        assertFalse(writer.hasPendingData());
        verify(3, postRequestedFor(urlEqualTo("/_bulk")));
        verify(postRequestedFor(urlEqualTo("/_bulk")).withRequestBody(equalTo(event1 + event2)));
        verify(postRequestedFor(urlEqualTo("/_bulk")).withRequestBody(equalTo(event3 + event4)));
    }

    @Test
    public void should_split_mixed_delete_and_index_events_on_413() throws Exception {
        String delete1 = "{\"delete\":{\"_index\":\"logs\",\"_id\":\"delete-1\"}}\n";
        String index1 = "{\"index\":{}}\n{\"message\":\"index-1\"}\n";
        String delete2 = "{\"delete\":{\"_index\":\"logs\",\"_id\":\"delete-2\"}}\n";
        String index2 = "{\"index\":{}}\n{\"message\":\"index-2\"}\n";

        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(1)
                .withRequestBody(containing("delete-1"))
                .withRequestBody(containing("index-2"))
                .willReturn(aResponse().withStatus(413)));
        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200)));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, delete1 + index1 + delete2 + index2);
        writer.sendData();

        assertFalse(writer.hasPendingData());
        verify(3, postRequestedFor(urlEqualTo("/_bulk")));
        verify(postRequestedFor(urlEqualTo("/_bulk")).withRequestBody(equalTo(delete1 + index1)));
        verify(postRequestedFor(urlEqualTo("/_bulk")).withRequestBody(equalTo(delete2 + index2)));
    }

    @Test
    public void should_drop_only_single_event_that_still_returns_413() throws Exception {
        String oversizedEvent = "{\"index\":{}}\n{\"message\":\"oversized-event\"}\n";
        String normalEvent = "{\"index\":{}}\n{\"message\":\"normal-event\"}\n";

        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(1)
                .withRequestBody(containing("oversized-event"))
                .willReturn(aResponse().withStatus(413).withBody("payload too large")));
        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, oversizedEvent + normalEvent);
        writer.sendData();

        assertFalse(writer.hasPendingData());
        verify(3, postRequestedFor(urlEqualTo("/_bulk")));
        verify(postRequestedFor(urlEqualTo("/_bulk")).withRequestBody(equalTo(oversizedEvent)));
        verify(postRequestedFor(urlEqualTo("/_bulk")).withRequestBody(equalTo(normalEvent)));
    }

    @Test
    public void should_keep_unsent_events_in_buffer_when_later_split_request_fails() throws Exception {
        String event1 = "{\"index\":{}}\n{\"message\":\"event-1\"}\n";
        String event2 = "{\"index\":{}}\n{\"message\":\"event-2\"}\n";
        String event3 = "{\"index\":{}}\n{\"message\":\"event-3\"}\n";
        String event4 = "{\"index\":{}}\n{\"message\":\"event-4\"}\n";

        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(1)
                .withRequestBody(containing("event-1"))
                .withRequestBody(containing("event-4"))
                .willReturn(aResponse().withStatus(413)));
        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(2)
                .withRequestBody(containing("event-1"))
                .withRequestBody(containing("event-2"))
                .willReturn(aResponse().withStatus(200)));
        stubFor(post(urlEqualTo("/_bulk"))
                .atPriority(2)
                .withRequestBody(containing("event-3"))
                .withRequestBody(containing("event-4"))
                .willReturn(aResponse().withStatus(500).withBody("server error")));

        Settings settings = settings();
        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, event1 + event2 + event3 + event4);

        try {
            writer.sendData();
            fail("Expected IOException on 500");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("500"));
        }

        assertTrue(writer.hasPendingData());
        assertEquals(event3 + event4, writer.getSendBuffer().toString());
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

    @Test
    public void should_use_url_userinfo_for_basic_auth_end_to_end() throws Exception {
        // Legacy form: credentials embedded in the URL userInfo (URL-encoded UTF-8), no explicit
        // username/password configured. The writer must (a) strip userInfo before handing the URI to
        // java.net.http.HttpClient (which rejects userInfo URIs) and (b) decode it and feed
        // BasicAuthentication so the Authorization header is actually sent. This is the integrated
        // replacement for the old BasicAuthentication "fallback to URL credentials" unit test.
        stubFor(post(urlEqualTo("/_bulk")).willReturn(aResponse().withStatus(200)));

        Settings settings = new Settings();
        settings.setUrl(new URL("http://user:p%40ss%E2%82%ACw%C3%B6rd%23123@localhost:" + server.port() + "/_bulk"));
        settings.setReadTimeout(5000);
        settings.setConnectTimeout(5000);
        settings.setAuthentication(new com.agido.logback.elasticsearch.config.BasicAuthentication());

        ElasticsearchWriter writer = new ElasticsearchWriter(errorReporter(settings), settings, headers(null, null));
        write(writer, "{\"index\":{}}\n");
        writer.sendData(); // must NOT throw: userInfo is stripped before HttpClient sees the URI

        assertFalse(writer.hasPendingData());
        String expected = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("user:p@ss€wörd#123".getBytes(StandardCharsets.UTF_8));
        verify(postRequestedFor(urlEqualTo("/_bulk"))
                .withHeader("Authorization", equalTo(expected)));
    }
}
