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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
            URI rawUri = settings.getUrl().toURI();
            String userInfo = rawUri.getUserInfo();
            URI sendUri = stripUserInfo(rawUri);
            String data = sendBuffer.toString();

            HttpResponse<byte[]> response = sendRequest(sendUri, userInfo, data);
            int rc = response.statusCode();

            if (rc == 200) {
                clearSendBufferAfterSuccess();
                return;
            }

            if (rc == 413) {
                List<String> events = splitBulkEvents(data);
                if (!events.isEmpty()) {
                    handlePayloadTooLarge(sendUri, userInfo, events);
                    resetBufferExceededWhenEmpty();
                    return;
                }
            }

            handleFailedResponse(response);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + settings.getUrl(), e);
        }
    }

    /**
     * Splits a request rejected with HTTP 413 at event boundaries. An event is
     * dropped only if it is still rejected after being isolated.
     */
    private void handlePayloadTooLarge(URI sendUri, String userInfo, List<String> events) throws IOException {
        if (events.size() == 1) {
            dropOversizedEvent(events.get(0));
            return;
        }

        int middle = events.size() / 2;
        errorReporter.logWarning("Elasticsearch rejected a bulk request containing "
                + events.size() + " events with HTTP 413. Splitting it into "
                + middle + " and " + (events.size() - middle) + " events.");

        sendEventRange(sendUri, userInfo, events, 0, middle);
        sendEventRange(sendUri, userInfo, events, middle, events.size());
    }

    private void sendEventRange(URI sendUri, String userInfo, List<String> events,
                                int fromIndex, int toIndex) throws IOException {
        String data = joinEvents(events, fromIndex, toIndex);
        HttpResponse<byte[]> response = sendRequest(sendUri, userInfo, data);
        int rc = response.statusCode();

        if (rc == 200) {
            removeProcessedPrefix(data);
            return;
        }

        if (rc == 413) {
            int eventCount = toIndex - fromIndex;
            if (eventCount == 1) {
                dropOversizedEvent(data);
                return;
            }

            int middle = fromIndex + eventCount / 2;
            errorReporter.logWarning("Elasticsearch rejected a bulk request containing "
                    + eventCount + " events with HTTP 413. Splitting it into "
                    + (middle - fromIndex) + " and " + (toIndex - middle) + " events.");

            sendEventRange(sendUri, userInfo, events, fromIndex, middle);
            sendEventRange(sendUri, userInfo, events, middle, toIndex);
            return;
        }

        handleFailedResponse(response);
    }

    private HttpResponse<byte[]> sendRequest(URI sendUri, String userInfo, String data) throws IOException {
        byte[] body = buildBody(data);
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
            // AWS Signature V4 includes the body, so each split request must be signed again.
            authentication.addAuth(requestHeaders, sendUri, body);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(sendUri)
                .timeout(Duration.ofMillis(settings.getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }

        try {
            return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending data to server", e);
        }
    }

    private void handleFailedResponse(HttpResponse<byte[]> response) throws IOException {
        int rc = response.statusCode();
        String data = new String(response.body(), StandardCharsets.UTF_8);
        if (rc >= 400 && rc < 500) {
            errorReporter.logInfo("Send queue cleared - drop log messages due to http 4xx error.");
            sendBuffer.setLength(0);
            bufferExceeded = false;
        }
        throw new IOException("Got response code [" + rc + "] from server with data " + data);
    }

    /**
     * Parses the NDJSON generated by this appender into complete bulk events.
     * Index, create and update have an action and source line; delete has only
     * an action line. An empty result means the buffer cannot be split safely.
     */
    private static List<String> splitBulkEvents(String bulkData) {
        List<String> lines = splitLinesKeepingNewline(bulkData);
        List<String> events = new ArrayList<>();
        int index = 0;

        while (index < lines.size()) {
            String actionLine = lines.get(index);
            if (actionLine.trim().isEmpty()) {
                index++;
                continue;
            }

            String operation = getBulkOperation(actionLine);
            if (operation == null) {
                return Collections.emptyList();
            }

            StringBuilder event = new StringBuilder(actionLine);
            index++;
            if (!"delete".equals(operation)) {
                if (index >= lines.size() || lines.get(index).trim().isEmpty()) {
                    return Collections.emptyList();
                }
                event.append(lines.get(index));
                index++;
            }
            events.add(event.toString());
        }

        return events;
    }

    private static List<String> splitLinesKeepingNewline(String value) {
        List<String> lines = new ArrayList<>();
        int lineStart = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                lines.add(value.substring(lineStart, i + 1));
                lineStart = i + 1;
            }
        }
        if (lineStart < value.length()) {
            lines.add(value.substring(lineStart));
        }
        return lines;
    }

    private static String getBulkOperation(String actionLine) {
        String trimmed = actionLine.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        for (String operation : new String[]{"index", "create", "update", "delete"}) {
            if (trimmed.matches("^\\{\\s*\"" + operation + "\"\\s*:.*")) {
                return operation;
            }
        }
        return null;
    }

    private static String joinEvents(List<String> events, int fromIndex, int toIndex) {
        StringBuilder result = new StringBuilder();
        for (int i = fromIndex; i < toIndex; i++) {
            result.append(events.get(i));
        }
        return result.toString();
    }

    private void dropOversizedEvent(String event) throws IOException {
        errorReporter.logWarning("Dropping one log event because Elasticsearch rejected the isolated event "
                + "with HTTP 413. Event size: "
                + event.getBytes(StandardCharsets.UTF_8).length + " bytes.");
        removeProcessedPrefix(event);
    }

    /** Removes data already sent successfully or intentionally dropped. */
    private void removeProcessedPrefix(String data) throws IOException {
        if (!startsWith(sendBuffer, data)) {
            throw new IOException("Internal send buffer mismatch while removing processed bulk events");
        }
        sendBuffer.delete(0, data.length());
    }

    private static boolean startsWith(StringBuilder value, String prefix) {
        if (prefix.length() > value.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (value.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private void clearSendBufferAfterSuccess() {
        sendBuffer.setLength(0);
        resetBufferExceededWhenEmpty();
    }

    private void resetBufferExceededWhenEmpty() {
        if (sendBuffer.length() == 0 && bufferExceeded) {
            errorReporter.logInfo("Send queue cleared - log messages will no longer be lost");
            bufferExceeded = false;
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
