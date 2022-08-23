package com.agido.logback.elasticsearch;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.core.Context;
import com.agido.logback.elasticsearch.config.ElasticsearchProperties;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Property;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.AbstractPropertyAndEncoder;
import com.agido.logback.elasticsearch.util.AccessPropertyAndEncoder;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

public class AccessElasticsearchPublisher extends AbstractElasticsearchPublisher<IAccessEvent> {

    public AccessElasticsearchPublisher(Context context, ErrorReporter errorReporter, Settings settings, ElasticsearchProperties properties, HttpRequestHeaders httpRequestHeaders) throws IOException {
        super(context, errorReporter, settings, properties, httpRequestHeaders);
    }

    @Override
    protected AbstractPropertyAndEncoder<IAccessEvent> buildPropertyAndEncoder(Context context, Property property) {
        return new AccessPropertyAndEncoder(property, context);
    }

    @Override
    protected void serializeCommonFields(JsonGenerator gen, IAccessEvent event) throws IOException {
        gen.writeObjectField("@timestamp", getTimestamp(event.getTimeStamp()));
    }
}
