package com.agido.logback.elasticsearch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import com.agido.logback.elasticsearch.config.Property;
import com.agido.logback.elasticsearch.util.ClassicPropertyAndEncoder;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PropertySerializerTest {
    @Mock
    private Context context;

    @Mock
    private JsonGenerator jsonGenerator;

    @Mock
    private ILoggingEvent loggingEvent;

    private PropertySerializer<ILoggingEvent> propertySerializer = new PropertySerializer<>();

    @Test
    public void should_default_to_string_type() throws Exception {
        // given
        Property property = new Property();
        property.setValue("propertyValue");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        assertThat(property.getType(), is(Property.Type.STRING));
        verify(jsonGenerator).writeObjectField(null, "propertyValue");
    }

    @Test
    public void should_serialize_int_as_number() throws Exception {
        // given
        Property property = new Property();
        property.setValue("123");
        property.setType("int");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        verify(jsonGenerator).writeNumberField(null, 123);
    }

    @Test
    public void should_serialize_object_when_invalid_int() throws Exception {
        // given
        Property property = new Property();
        property.setValue("A123Z");
        property.setType("int");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        verify(jsonGenerator).writeObjectField(null, "A123Z");
    }

    @Test
    public void should_serialize_float_as_number() throws Exception {
        // given
        Property property = new Property();
        property.setValue("12.30");
        property.setType("float");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        verify(jsonGenerator).writeNumberField(null, 12.30f);
    }

    @Test
    public void should_serialize_object_when_invalid_float() throws Exception {
        // given
        Property property = new Property();
        property.setValue("A12.30Z");
        property.setType("float");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        verify(jsonGenerator).writeObjectField(null, "A12.30Z");
    }

    @Test
    public void should_serialize_true_as_boolean() throws Exception {
        // given
        Property property = new Property();
        property.setValue("true");
        property.setType("boolean");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        verify(jsonGenerator).writeBooleanField(null, true);
    }

    @Test
    public void should_serialize_object_when_invalid_boolean() throws Exception {
        // given
        Property property = new Property();
        property.setValue("AtrueZ");
        property.setType("boolean");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        verify(jsonGenerator).writeObjectField(null, "AtrueZ");
    }

    @Test
    public void should_serialize_object_when_invalid_type() throws Exception {
        // given
        Property property = new Property();
        property.setValue("value");
        property.setType("invalidType");

        // when
        propertySerializer.serializeProperty(jsonGenerator, loggingEvent, new ClassicPropertyAndEncoder(property, context));

        // then
        verify(jsonGenerator).writeObjectField(null, "value");
    }
}
