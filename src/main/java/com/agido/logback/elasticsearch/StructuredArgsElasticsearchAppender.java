package com.agido.logback.elasticsearch;

import com.agido.logback.elasticsearch.config.Settings;

import java.io.IOException;

public class StructuredArgsElasticsearchAppender extends ElasticsearchAppender {

    public StructuredArgsElasticsearchAppender() {
    }

    public StructuredArgsElasticsearchAppender(Settings settings) {
        super(settings);
    }

    protected StructuredArgsElasticsearchPublisher buildElasticsearchPublisher() throws IOException {
        return new StructuredArgsElasticsearchPublisher(this.getContext(), this.errorReporter, this.settings,
                this.elasticsearchProperties, this.headers);
    }

}
