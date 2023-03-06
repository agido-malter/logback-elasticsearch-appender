package com.agido.logback.elasticsearch.config;

import java.util.ArrayList;
import java.util.List;

/**
 * this holds the information from the appender/properties tag (in logback.xml)
 */
public class ElasticsearchProperties {

    private List<Property> properties;

    public ElasticsearchProperties() {
        this.properties = new ArrayList<Property>();
    }

    public List<Property> getProperties() {
        return properties;
    }

    /**
     * this is called by logback for each property tag contained in the properties tag
     */
    public void addProperty(Property property) {
        properties.add(property);
    }

    /**
     * this is called by logback for each esProperty tag contained in the properties tag
     */
    public void addEsProperty(Property property) {
        properties.add(property);
    }

}
