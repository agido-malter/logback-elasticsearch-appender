package com.agido.logback.elasticsearch.util;

import ch.qos.logback.access.common.PatternLayout;
import ch.qos.logback.access.common.spi.IAccessEvent;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.pattern.PatternLayoutBase;
import com.agido.logback.elasticsearch.config.Property;

public class AccessPropertyAndEncoder extends AbstractPropertyAndEncoder<IAccessEvent> {

    public AccessPropertyAndEncoder(Property property, Context context) {
        super(property, context);
    }

    @Override
    protected PatternLayoutBase<IAccessEvent> getLayout() {
        return new PatternLayout();
    }
}
