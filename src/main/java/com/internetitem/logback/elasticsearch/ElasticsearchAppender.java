package com.internetitem.logback.elasticsearch;

import com.agido.logback.elasticsearch.config.Settings;

/**
 * compatibility class
 *
 * @deprecated since 3.0.1, to be removed
 */
@Deprecated
public class ElasticsearchAppender extends com.agido.logback.elasticsearch.ElasticsearchAppender {
    public ElasticsearchAppender() {
        super();
    }

    public ElasticsearchAppender(Settings settings) {
        super(settings);
    }
}