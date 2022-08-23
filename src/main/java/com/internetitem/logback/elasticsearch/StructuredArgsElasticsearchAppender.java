package com.internetitem.logback.elasticsearch;

import com.agido.logback.elasticsearch.config.Settings;

/**
 * compatibility class
 *
 * @deprecated since 3.0.1, to be removed
 */
@Deprecated
public class StructuredArgsElasticsearchAppender extends com.agido.logback.elasticsearch.StructuredArgsElasticsearchAppender {
    public StructuredArgsElasticsearchAppender() {
        super();
    }

    public StructuredArgsElasticsearchAppender(Settings settings) {
        super(settings);
    }
}