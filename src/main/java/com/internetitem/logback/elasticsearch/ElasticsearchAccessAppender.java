package com.internetitem.logback.elasticsearch;

import com.agido.logback.elasticsearch.config.Settings;

/**
 * compatibility class
 *
 * @deprecated since 3.0.1, to be removed
 */
@Deprecated
public class ElasticsearchAccessAppender extends com.agido.logback.elasticsearch.ElasticsearchAccessAppender {
    public ElasticsearchAccessAppender() {
        super();
    }

    public ElasticsearchAccessAppender(Settings settings) {
        super(settings);
    }
}