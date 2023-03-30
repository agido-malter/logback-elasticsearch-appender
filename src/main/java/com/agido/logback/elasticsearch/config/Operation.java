package com.agido.logback.elasticsearch.config;

import java.util.Optional;

/**
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html">Bulk API actions</a>
 */
public enum Operation {
    index,
    create,
    update,
    delete;

    public static Optional<Operation> of( String value ) {
        try {
            return Optional.of( valueOf( value ) );
        } catch ( IllegalArgumentException ignored ) {
        }

        return Optional.empty( );
    }
}
