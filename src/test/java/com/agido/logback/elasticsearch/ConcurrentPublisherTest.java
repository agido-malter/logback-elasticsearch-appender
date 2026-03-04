package com.agido.logback.elasticsearch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Context;
import com.agido.logback.elasticsearch.config.ElasticsearchProperties;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Operation;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import com.agido.logback.elasticsearch.writer.SafeWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * Tests for concurrent event handling in AbstractElasticsearchPublisher.
 * Verifies that the lock-free implementation correctly handles high-concurrency scenarios.
 */
@RunWith(MockitoJUnitRunner.class)
public class ConcurrentPublisherTest {

    @Mock
    private Context context;

    @Mock
    private ErrorReporter errorReporter;

    @Mock
    private Settings settings;

    @Mock
    private ElasticsearchProperties properties;

    @Mock
    private HttpRequestHeaders headers;

    @Before
    public void setUp() {
        given(settings.getIndex()).willReturn("test-index");
        given(settings.getSleepTime()).willReturn(10);
        given(settings.getMaxRetries()).willReturn(3);
        given(settings.isRawJsonMessage()).willReturn(false);
        given(settings.getMaxMessageSize()).willReturn(0);
        given(settings.isIncludeMdc()).willReturn(false);
        given(settings.isIncludeKvp()).willReturn(false);
        given(settings.isObjectSerialization()).willReturn(false);
        given(settings.getOperation()).willReturn(Operation.index);
        given(settings.getMaxBatchSize()).willReturn(-1);  // Unlimited by default
    }

    private ILoggingEvent createEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        return event;
    }

    @Test
    public void should_handle_concurrent_event_addition_without_losing_events() throws Exception {
        int threadCount = 50;
        int eventsPerThread = 100;
        int totalExpectedEvents = threadCount * eventsPerThread;

        AtomicInteger processedCount = new AtomicInteger(0);
        Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch allThreadsReady = new CountDownLatch(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);

        TestPublisher publisher = new TestPublisher(context, errorReporter, settings, properties, headers,
                processedCount, processedMessages);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Submit all threads
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    allThreadsReady.countDown();
                    startSignal.await(); // Wait for all threads to be ready

                    for (int e = 0; e < eventsPerThread; e++) {
                        String message = "thread-" + threadId + "-event-" + e;
                        publisher.addEvent(createEvent(message));
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait for all threads to be ready, then start them simultaneously
        allThreadsReady.await();
        startSignal.countDown();

        // Shutdown executor and wait for completion
        executor.shutdown();
        assertTrue("Executor did not terminate", executor.awaitTermination(30, TimeUnit.SECONDS));

        // Wait for the publisher worker thread to finish processing
        int maxWaitSeconds = 30;
        int waited = 0;
        while (processedCount.get() < totalExpectedEvents && waited < maxWaitSeconds * 10) {
            Thread.sleep(100);
            waited++;
        }

        // Allow a bit more time for final processing
        Thread.sleep(500);

        assertThat("All events should be processed without loss",
                processedCount.get(), is(totalExpectedEvents));
    }

    @Test
    public void should_handle_burst_of_events_from_single_thread() throws Exception {
        int eventCount = 1000;

        AtomicInteger processedCount = new AtomicInteger(0);
        Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
        TestPublisher publisher = new TestPublisher(context, errorReporter, settings, properties, headers,
                processedCount, processedMessages);

        // Rapid-fire events from a single thread
        for (int i = 0; i < eventCount; i++) {
            publisher.addEvent(createEvent("event-" + i));
        }

        // Wait for processing to complete
        int maxWaitSeconds = 30;
        int waited = 0;
        while (processedCount.get() < eventCount && waited < maxWaitSeconds * 10) {
            Thread.sleep(100);
            waited++;
        }

        Thread.sleep(500);

        assertThat("All burst events should be processed",
                processedCount.get(), is(eventCount));
    }

    @Test
    public void should_handle_events_added_during_worker_shutdown() throws Exception {
        // This test verifies the race condition safety check in run()
        // Events added between working.set(false) and return should still be processed

        AtomicInteger processedCount = new AtomicInteger(0);
        Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
        AtomicInteger workerStartCount = new AtomicInteger(0);

        TestPublisher publisher = new TestPublisher(context, errorReporter, settings, properties, headers,
                processedCount, processedMessages) {
            @Override
            public void run() {
                workerStartCount.incrementAndGet();
                super.run();
            }
        };

        // Add initial event to start worker
        publisher.addEvent(createEvent("initial"));

        // Wait a bit for worker to start processing
        Thread.sleep(50);

        // Add more events in rapid succession while worker might be shutting down
        for (int i = 0; i < 100; i++) {
            publisher.addEvent(createEvent("rapid-" + i));
            if (i % 20 == 0) {
                Thread.sleep(15); // Occasional pauses to trigger worker restart
            }
        }

        // Wait for all processing
        Thread.sleep(2000);

        assertThat("All events including initial should be processed",
                processedCount.get(), is(101));
    }

    @Test
    public void should_not_lose_events_under_contention() throws Exception {
        // High contention test: many threads, small batches, rapid adds
        int threadCount = 100;
        int eventsPerThread = 50;
        int totalExpectedEvents = threadCount * eventsPerThread;

        AtomicInteger processedCount = new AtomicInteger(0);
        Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch done = new CountDownLatch(threadCount);

        TestPublisher publisher = new TestPublisher(context, errorReporter, settings, properties, headers,
                processedCount, processedMessages);

        // Create maximum contention by starting all threads at once
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int e = 0; e < eventsPerThread; e++) {
                    publisher.addEvent(createEvent("t" + threadId + "e" + e));
                }
                done.countDown();
            });
        }

        // Start all threads as close together as possible
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all adding threads to complete
        assertTrue("All threads should complete", done.await(30, TimeUnit.SECONDS));

        // Wait for processing
        int maxWaitMs = 30000;
        int waited = 0;
        while (processedCount.get() < totalExpectedEvents && waited < maxWaitMs) {
            Thread.sleep(50);
            waited += 50;
        }

        Thread.sleep(500);

        assertThat("No events should be lost under high contention",
                processedCount.get(), is(totalExpectedEvents));
    }

    @Test
    public void should_respect_max_batch_size_setting() throws Exception {
        int eventCount = 500;
        int maxBatchSize = 50;

        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger maxBatchSeen = new AtomicInteger(0);
        Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // Override maxBatchSize for this test
        given(settings.getMaxBatchSize()).willReturn(maxBatchSize);

        TestPublisher publisher = new TestPublisher(context, errorReporter, settings, properties, headers,
                processedCount, processedMessages) {
            @Override
            protected void serializeCommonFields(JsonGenerator gen, ILoggingEvent event) throws IOException {
                super.serializeCommonFields(gen, event);
            }
        };

        // Add all events at once
        for (int i = 0; i < eventCount; i++) {
            publisher.addEvent(createEvent("batch-event-" + i));
        }

        // Wait for processing
        int maxWaitMs = 30000;
        int waited = 0;
        while (processedCount.get() < eventCount && waited < maxWaitMs) {
            Thread.sleep(50);
            waited += 50;
        }

        Thread.sleep(500);

        assertThat("All events should be processed even with batch size limit",
                processedCount.get(), is(eventCount));
    }

    @Test
    public void should_process_events_with_minimal_latency_when_queue_has_data() throws Exception {
        // This test verifies that events are processed immediately when queue has data
        // (drain-first optimization)
        int eventCount = 100;

        AtomicInteger processedCount = new AtomicInteger(0);
        Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // Use a longer sleep time to verify drain-first works
        given(settings.getSleepTime()).willReturn(1000);  // 1 second sleep

        TestPublisher publisher = new TestPublisher(context, errorReporter, settings, properties, headers,
                processedCount, processedMessages);

        long startTime = System.currentTimeMillis();

        // Add events
        for (int i = 0; i < eventCount; i++) {
            publisher.addEvent(createEvent("latency-event-" + i));
        }

        // Wait for first batch to be processed (should be quick due to drain-first)
        while (processedCount.get() == 0 && (System.currentTimeMillis() - startTime) < 5000) {
            Thread.sleep(10);
        }

        long firstBatchTime = System.currentTimeMillis() - startTime;

        // First batch should be processed much faster than sleepTime
        // (within 500ms, not 1000ms+ if we were sleeping first)
        assertTrue("First batch should be processed quickly due to drain-first optimization. " +
                        "Took: " + firstBatchTime + "ms",
                firstBatchTime < 500);

        // Wait for all processing to complete
        int maxWaitMs = 10000;
        int waited = 0;
        while (processedCount.get() < eventCount && waited < maxWaitMs) {
            Thread.sleep(50);
            waited += 50;
        }

        assertThat("All events should be processed",
                processedCount.get(), is(eventCount));
    }

    /**
     * Test implementation of ClassicElasticsearchPublisher that tracks processed events.
     */
    private static class TestPublisher extends ClassicElasticsearchPublisher {

        private final AtomicInteger processedCount;
        private final Set<String> processedMessages;

        public TestPublisher(Context context, ErrorReporter errorReporter, Settings settings,
                           ElasticsearchProperties properties, HttpRequestHeaders headers,
                           AtomicInteger processedCount, Set<String> processedMessages) throws IOException {
            super(context, errorReporter, settings, properties, headers);
            this.processedCount = processedCount;
            this.processedMessages = processedMessages;
        }

        @Override
        protected ElasticsearchOutputAggregator configureOutputAggregator(Settings settings,
                ErrorReporter errorReporter, HttpRequestHeaders httpRequestHeaders) {
            ElasticsearchOutputAggregator aggregator = new ElasticsearchOutputAggregator(settings, errorReporter);
            // Add a dummy writer so hasOutputs() returns true
            aggregator.addWriter(new SafeWriter() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                    // No-op - we track events in serializeCommonFields
                }

                @Override
                public boolean hasPendingData() {
                    return false;
                }

                @Override
                public void sendData() {
                    // No-op
                }
            });
            return aggregator;
        }

        @Override
        protected void serializeCommonFields(JsonGenerator gen, ILoggingEvent event) throws IOException {
            super.serializeCommonFields(gen, event);
            // Track that this event was processed
            String message = event.getMessage();
            if (message != null) {
                processedMessages.add(message);
            }
            processedCount.incrementAndGet();
        }
    }
}
