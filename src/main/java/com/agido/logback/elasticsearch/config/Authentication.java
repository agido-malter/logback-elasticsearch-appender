package com.agido.logback.elasticsearch.config;

import java.net.URI;
import java.util.Map;

public interface Authentication {
    /**
     * Add authentication headers for whatever authentication scheme is used.
     *
     * @param headers the mutable map of request headers; implementations add their auth headers here
     * @param uri     the request URI (with any userInfo stripped off)
     * @param body    the exact bytes that will be sent (already gzipped if compression is enabled)
     */
    void addAuth(Map<String, String> headers, URI uri, byte[] body);
}
