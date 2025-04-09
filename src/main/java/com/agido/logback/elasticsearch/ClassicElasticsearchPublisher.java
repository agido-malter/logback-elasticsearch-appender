package com.agido.logback.elasticsearch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import com.agido.logback.elasticsearch.config.ElasticsearchProperties;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Property;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.AbstractPropertyAndEncoder;
import com.agido.logback.elasticsearch.util.ClassicPropertyAndEncoder;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.event.KeyValuePair;

import java.io.IOException;
import java.util.*;

public class ClassicElasticsearchPublisher extends AbstractElasticsearchPublisher<ILoggingEvent> {

    public ClassicElasticsearchPublisher(Context context, ErrorReporter errorReporter, Settings settings, ElasticsearchProperties properties, HttpRequestHeaders headers) throws IOException {
        super(context, errorReporter, settings, properties, headers);
    }

    @Override
    protected AbstractPropertyAndEncoder<ILoggingEvent> buildPropertyAndEncoder(Context context, Property property) {
        return new ClassicPropertyAndEncoder(property, context);
    }

    @Override
    protected void serializeCommonFields(JsonGenerator gen, ILoggingEvent event) throws IOException {
        gen.writeObjectField("@timestamp", getTimestamp(event.getTimeStamp()));

        if (settings.isRawJsonMessage()) {
            gen.writeFieldName("message");
            gen.writeRawValue(event.getFormattedMessage());
        } else {
            String formattedMessage = event.getFormattedMessage();
            if (settings.getMaxMessageSize() > 0 && formattedMessage != null && formattedMessage.length() > settings.getMaxMessageSize()) {
                formattedMessage = formattedMessage.substring(0, settings.getMaxMessageSize()) + "..";
            }
            gen.writeObjectField("message", formattedMessage);
        }

        if (settings.isIncludeMdc()) {
            for (Map.Entry<String, String> entry : event.getMDCPropertyMap().entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
        }

        if (settings.isIncludeKvp()) {
          final List<KeyValuePair> kvps = event.getKeyValuePairs() != null ? event.getKeyValuePairs() : Collections.emptyList();
          for (KeyValuePair kvp : kvps) {
            if (kvp != null) {
              gen.writeObjectField(kvp.key, kvp.value);
            }
          }
        }
    }
}
