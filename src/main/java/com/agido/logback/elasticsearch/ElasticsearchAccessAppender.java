package com.agido.logback.elasticsearch;

import ch.qos.logback.access.spi.IAccessEvent;
import com.agido.logback.elasticsearch.config.Settings;

import java.io.IOException;

public class ElasticsearchAccessAppender extends AbstractElasticsearchAppender<IAccessEvent> {

    public ElasticsearchAccessAppender() {
    }

    public ElasticsearchAccessAppender(Settings settings) {
        super(settings);
    }

    @Override
    protected void appendInternal(IAccessEvent eventObject) {
        eventObject.prepareForDeferredProcessing();
        publishEvent(eventObject);
    }

    protected AccessElasticsearchPublisher buildElasticsearchPublisher() throws IOException {
        return new AccessElasticsearchPublisher(getContext(), errorReporter, settings, elasticsearchProperties, headers);
    }


}
