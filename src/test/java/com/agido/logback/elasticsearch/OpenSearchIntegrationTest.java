package com.agido.logback.elasticsearch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.Assume;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end test enabled by the OpenSearch GitHub Actions workflow. It is
 * skipped during normal unit-test runs when OPENSEARCH_URL is not set.
 */
public class OpenSearchIntegrationTest {

    @Test
    public void should_send_log_event_to_opensearch_bulk_api() throws Exception {
        String configuredUrl = System.getenv("OPENSEARCH_URL");
        Assume.assumeTrue("OPENSEARCH_URL is required for this integration test",
                configuredUrl != null && !configuredUrl.trim().isEmpty());

        String baseUrl = stripTrailingSlash(configuredUrl.trim());
        String index = "logback-opensearch-it-" + UUID.randomUUID().toString().replace("-", "");
        String marker = "opensearch-integration-" + UUID.randomUUID();
        HttpClient client = HttpClient.newHttpClient();
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.start();

        ElasticsearchAppender appender = new ElasticsearchAppender();
        try {
            appender.setContext(context);
            appender.setName("OPENSEARCH_INTEGRATION");
            appender.setUrl(baseUrl + "/_bulk?refresh=wait_for");
            appender.setIndex(index);
            appender.setOperation("index");
            appender.setConnectTimeout(5_000);
            appender.setReadTimeout(10_000);
            appender.setShutdownTimeout(15_000);
            appender.setErrorsToStderr(true);
            appender.start();

            Logger logger = context.getLogger("opensearch-integration-test");
            LoggingEvent event = new LoggingEvent(
                    OpenSearchIntegrationTest.class.getName(),
                    logger,
                    Level.INFO,
                    marker,
                    null,
                    null);
            appender.doAppend(event);

            HttpResponse<String> response = waitForMarker(client, baseUrl, index, marker);

            assertEquals(response.body(), 200, response.statusCode());
            assertTrue("OpenSearch response did not contain marker " + marker + ": " + response.body(),
                    response.body().contains(marker));
        } finally {
            appender.stop();
            context.stop();
            client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/" + index))
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static HttpResponse<String> waitForMarker(HttpClient client, String baseUrl,
                                                       String index, String marker) throws Exception {
        HttpResponse<String> lastResponse = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            lastResponse = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/" + index + "/_search"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (lastResponse.statusCode() == 200 && lastResponse.body().contains(marker)) {
                return lastResponse;
            }
            Thread.sleep(250);
        }
        return lastResponse;
    }
}
