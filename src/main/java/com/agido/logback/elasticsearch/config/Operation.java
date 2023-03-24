package com.agido.logback.elasticsearch.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html">Bulk API actions</a>
 */
public enum Operation {
    INDEX( "index" ),
    CREATE( "create" ),
    UPDATE( "update" ),
    DELETE( "delete" );

    private final String label;

    Operation( String label ) {
        this.label = label;
    }

    private final static Map<String, Operation> cache;
    static {
        cache = new HashMap<>();
        for (Operation operation : values()) {
            cache.put(operation.name(), operation);
            cache.put(operation.label, operation);
        }
    }

    static Operation valueOfMap(String value) {
        return cache.get(value);
    }

    public static Optional<Operation> of( String value ) {
        return Optional.ofNullable( valueOfMap( value ) );
    }

    public String getLabel() {
        return this.label;
    }
}
