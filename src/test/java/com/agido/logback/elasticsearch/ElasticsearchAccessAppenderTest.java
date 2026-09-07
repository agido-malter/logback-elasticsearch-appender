package com.agido.logback.elasticsearch;

import ch.qos.logback.access.common.spi.IAccessEvent;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ElasticsearchAccessAppenderTest {

    @Test
    public void should_prepare_and_publish_access_events() {
        AccessElasticsearchPublisher mockedPublisher = mock(AccessElasticsearchPublisher.class);
        IAccessEvent event = mock(IAccessEvent.class);
        ElasticsearchAccessAppender appender = new ElasticsearchAccessAppender() {
            @Override
            protected AccessElasticsearchPublisher buildElasticsearchPublisher() throws IOException {
                return mockedPublisher;
            }
        };

        appender.start();
        appender.append(event);

        verify(event).prepareForDeferredProcessing();
        verify(mockedPublisher).addEvent(event);
    }

    @Test
    public void should_close_access_publisher_when_stopped() {
        AccessElasticsearchPublisher mockedPublisher = mock(AccessElasticsearchPublisher.class);
        ElasticsearchAccessAppender appender = new ElasticsearchAccessAppender() {
            @Override
            protected AccessElasticsearchPublisher buildElasticsearchPublisher() throws IOException {
                return mockedPublisher;
            }
        };

        appender.start();
        appender.stop();

        verify(mockedPublisher).close();
    }
}
