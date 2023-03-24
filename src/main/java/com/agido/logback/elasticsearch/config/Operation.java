package com.agido.logback.elasticsearch.config;

import java.util.EnumSet;
import java.util.Optional;

public enum Operation {
    INDEX( "index" ),
    CREATE( "create" );

    private final String label;

    Operation( String label ) {
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }

    public static Optional<Operation> of( String label ) {
        return EnumSet.allOf( Operation.class )
                      .stream()
                      .filter( op -> op.label.equalsIgnoreCase( label ) )
                      .findFirst();
    }
}
