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
 * End-to-end test enabled by the Elasticsearch and OpenSearch GitHub Actions
 * workflows. It is skipped during normal unit-test runs when
 * SEARCH_ENGINE_URL is not set.
 */
public class BulkApiIntegrationTest {

    @Test
    public void should_send_log_event_through_bulk_api() throws Exception {
        String configuredUrl = System.getenv("SEARCH_ENGINE_URL");
        Assume.assumeTrue("SEARCH_ENGINE_URL is required for this integration test",
                configuredUrl != null && !configuredUrl.trim().isEmpty());

        String engineName = System.getenv("SEARCH_ENGINE_NAME");
        if (engineName == null || engineName.trim().isEmpty()) {
            engineName = "search engine";
        }

        String baseUrl = stripTrailingSlash(configuredUrl.trim());
        String index = "logback-bulk-api-it-" + UUID.randomUUID().toString().replace("-", "");
        String marker = "bulk-api-integration-" + UUID.randomUUID();
        HttpClient client = HttpClient.newHttpClient();
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.start();

        ElasticsearchAppender appender = new ElasticsearchAppender();
        try {
            appender.setContext(context);
            appender.setName("BULK_API_INTEGRATION");
            appender.setUrl(baseUrl + "/_bulk?refresh=wait_for");
            appender.setIndex(index);
            appender.setOperation("index");
            appender.setConnectTimeout(5_000);
            appender.setReadTimeout(10_000);
            appender.setShutdownTimeout(15_000);
            appender.setErrorsToStderr(true);
            appender.start();

            Logger logger = context.getLogger("bulk-api-integration-test");
            LoggingEvent event = new LoggingEvent(
                    BulkApiIntegrationTest.class.getName(),
                    logger,
                    Level.INFO,
                    marker,
                    null,
                    null);
            appender.doAppend(event);

            HttpResponse<String> response = waitForMarker(client, baseUrl, index, marker);

            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(engineName + " response did not contain marker " + marker + ": " + response.body(),
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
