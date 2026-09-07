package com.agido.logback.elasticsearch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import com.agido.logback.elasticsearch.config.ElasticsearchProperties;
import com.agido.logback.elasticsearch.config.HttpRequestHeaders;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import tools.jackson.core.JsonGenerator;
import net.logstash.logback.marker.ObjectAppendingMarker;

import java.io.IOException;
import java.lang.reflect.Field;

public class StructuredArgsElasticsearchPublisher extends ClassicElasticsearchPublisher {
    private final String keyPrefix;
    private final Field fieldValue;
    private final ErrorReporter errorReporter;

    public StructuredArgsElasticsearchPublisher(Context context, ErrorReporter errorReporter, Settings settings, ElasticsearchProperties properties,
                                                HttpRequestHeaders headers) throws IOException {
        super(context, errorReporter, settings, properties, headers);

        this.errorReporter = errorReporter;

        String configuredKeyPrefix = "";
        if (settings != null && settings.getKeyPrefix() != null) {
            configuredKeyPrefix = settings.getKeyPrefix();
        }
        this.keyPrefix = configuredKeyPrefix;

        Field resolvedField = null;
        try {
            resolvedField = ObjectAppendingMarker.class.getDeclaredField("fieldValue");
            resolvedField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // message will be logged without object
            errorReporter.logError("error in logging with object serialization", e);
        }
        this.fieldValue = resolvedField;
    }

    protected void serializeCommonFields(JsonGenerator gen, ILoggingEvent event) throws IOException {
        super.serializeCommonFields(gen, event);

        if (event.getArgumentArray() != null) {
            Object[] eventArgs = event.getArgumentArray();
            for (Object eventArg : eventArgs) {
                if (eventArg instanceof ObjectAppendingMarker) {
                    ObjectAppendingMarker marker = (ObjectAppendingMarker) eventArg;
                    writeMarker(gen, marker);
                }
            }
        }
    }

    private void writeMarker(JsonGenerator gen, ObjectAppendingMarker marker) throws IOException {
        if (fieldValue == null) {
            return;
        }

        try {
            writeValueProperty(gen, keyPrefix + marker.getFieldName(), fieldValue.get(marker));
        } catch (IllegalAccessException e) {
            errorReporter.logError("error in logging with object serialization", e);
        }
    }

}
