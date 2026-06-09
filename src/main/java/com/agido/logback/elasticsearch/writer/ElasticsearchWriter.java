package com.agido.logback.elasticsearch.writer;

import com.agido.logback.elasticsearch.config.Authentication;
import com.agido.logback.elasticsearch.config.BasicAuthentication;
import com.agido.logback.elasticsearch.config.HttpRequestHeader;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.ErrorReporter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class ElasticsearchWriter implements SafeWriter {

    private StringBuilder sendBuffer;

    private ErrorReporter errorReporter;
    private Settings settings;
    private Collection<HttpRequestHeader> headerList;

    private boolean bufferExceeded;
    private boolean compressedTransfer;

    private final HttpClient httpClient;

    public ElasticsearchWriter(ErrorReporter errorReporter, Settings settings, HttpRequestHeaders headers) {
        this.errorReporter = errorReporter;
        this.settings = settings;
        this.headerList = headers != null && headers.getHeaders() != null
                ? headers.getHeaders()
                : Collections.emptyList();

        this.sendBuffer = new StringBuilder();
        compressedTransfer = false;
        for (HttpRequestHeader header : this.headerList) {
            if (header.getName().equalsIgnoreCase("Content-Encoding") && header.getValue().equals("gzip")) {
                compressedTransfer = true;
                break;
            }
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(settings.getConnectTimeout()))
                .build();
    }

    public void write(char[] cbuf, int off, int len) {
        if (bufferExceeded) {
            return;
        }

        sendBuffer.append(cbuf, off, len);

        if (sendBuffer.length() >= settings.getMaxQueueSize()) {
            errorReporter.logWarning("Send queue maximum size exceeded - log messages will be lost until the buffer is cleared");
            bufferExceeded = true;
        }
    }

    public void sendData() throws IOException {
        if (sendBuffer.length() <= 0) {
            return;
        }

        try {
            byte[] body = buildBody(sendBuffer.toString());

            URI rawUri = settings.getUrl().toURI();
            String userInfo = rawUri.getUserInfo();
            URI sendUri = stripUserInfo(rawUri);

            Map<String, String> requestHeaders = new LinkedHashMap<>();
            requestHeaders.put("Content-Type", "application/json");
            for (HttpRequestHeader header : headerList) {
                requestHeaders.put(header.getName(), header.getValue());
            }

            Authentication authentication = settings.getAuthentication();
            if (authentication != null) {
                if (userInfo != null && authentication instanceof BasicAuthentication
                        && !((BasicAuthentication) authentication).hasCredentials()) {
                    applyUserInfo((BasicAuthentication) authentication, userInfo);
                }
                authentication.addAuth(requestHeaders, sendUri, body);
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(sendUri)
                    .timeout(Duration.ofMillis(settings.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }

            HttpResponse<byte[]> response;
            try {
                response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while sending data to server", e);
            }

            int rc = response.statusCode();

            if (rc == 200) {
                sendBuffer.setLength(0);
                if (bufferExceeded) {
                    errorReporter.logInfo("Send queue cleared - log messages will no longer be lost");
                    bufferExceeded = false;
                }
            } else {
                String data = new String(response.body(), StandardCharsets.UTF_8);
                if (rc >= 400 && rc < 500) {
                    errorReporter.logInfo("Send queue cleared - drop log messages due to http 4xx error.");
                    sendBuffer.setLength(0);
                    bufferExceeded = false;
                }
                throw new IOException("Got response code [" + rc + "] from server with data " + data);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + settings.getUrl(), e);
        }
    }

    private byte[] buildBody(String body) throws IOException {
        if (compressedTransfer) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return baos.toByteArray();
        }
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static URI stripUserInfo(URI uri) throws URISyntaxException {
        if (uri.getUserInfo() == null) {
            return uri;
        }
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                uri.getPath(), uri.getQuery(), uri.getFragment());
    }

    private static void applyUserInfo(BasicAuthentication auth, String userInfo) {
        String decoded = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
        int idx = decoded.indexOf(':');
        if (idx >= 0) {
            auth.setUsername(decoded.substring(0, idx));
            auth.setPassword(decoded.substring(idx + 1));
        } else {
            auth.setUsername(decoded);
            auth.setPassword("");
        }
    }

    public boolean hasPendingData() {
        return sendBuffer.length() != 0;
    }

    public StringBuilder getSendBuffer() {
        return sendBuffer;
    }

    public Settings getSettings() {
        return settings;
    }

    public Collection<HttpRequestHeader> getHeaderList() {
        return headerList;
    }

    public ErrorReporter getErrorReporter() {
        return errorReporter;
    }

    public boolean isBufferExceeded() {
        return bufferExceeded;
    }

    public void setBufferExceeded(boolean bufferExceeded) {
        this.bufferExceeded = bufferExceeded;
    }

}
