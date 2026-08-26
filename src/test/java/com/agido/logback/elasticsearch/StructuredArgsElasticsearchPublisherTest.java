package com.agido.logback.elasticsearch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ContextBase;
import com.agido.logback.elasticsearch.config.Settings;
import com.agido.logback.elasticsearch.util.ErrorReporter;
import net.logstash.logback.marker.ObjectAppendingMarker;
import org.junit.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.StringWriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class StructuredArgsElasticsearchPublisherTest {

    @Test
    public void should_serialize_structured_arguments_with_key_prefix() throws Exception {
        Settings settings = settings(false);
        settings.setKeyPrefix("arg_");
        StructuredArgsElasticsearchPublisher publisher = publisher(settings);
        ILoggingEvent event = event(new ObjectAppendingMarker("answer", 42));

        JsonNode result = serialize(publisher, event);

        assertThat(result.get("arg_answer").intValue(), is(42));
    }

    @Test
    public void should_serialize_structured_objects_when_enabled() throws Exception {
        StructuredArgsElasticsearchPublisher publisher = publisher(settings(true));
        ILoggingEvent event = event(new ObjectAppendingMarker("payload", new TestPayload("value")));

        JsonNode result = serialize(publisher, event);

        assertThat(result.get("payload").get("name").stringValue(), is("value"));
    }

    private StructuredArgsElasticsearchPublisher publisher(Settings settings) throws Exception {
        return new StructuredArgsElasticsearchPublisher(
                new ContextBase(), mock(ErrorReporter.class), settings, null, null);
    }

    private Settings settings(boolean objectSerialization) {
        Settings settings = new Settings();
        settings.setIndex("test-index");
        settings.setObjectSerialization(objectSerialization);
        return settings;
    }

    private ILoggingEvent event(ObjectAppendingMarker marker) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        given(event.getTimeStamp()).willReturn(0L);
        given(event.getFormattedMessage()).willReturn("message");
        given(event.getArgumentArray()).willReturn(new Object[]{marker});
        return event;
    }

    private JsonNode serialize(StructuredArgsElasticsearchPublisher publisher, ILoggingEvent event) throws Exception {
        StringWriter output = new StringWriter();
        JsonMapper mapper = JsonMapper.builder().build();
        try (JsonGenerator generator = mapper.createGenerator(output)) {
            generator.writeStartObject();
            publisher.serializeCommonFields(generator, event);
            generator.writeEndObject();
        }
        return mapper.readTree(output.toString());
    }

    public static class TestPayload {
        private final String name;

        public TestPayload(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
