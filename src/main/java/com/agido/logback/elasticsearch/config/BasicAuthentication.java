package com.agido.logback.elasticsearch.config;

import com.agido.logback.elasticsearch.util.Base64;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BasicAuthentication implements Authentication {

    private volatile String cachedAuthHeader;
    private String username;
    private String password;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return true if both username and password have been configured explicitly.
     */
    public boolean hasCredentials() {
        return username != null && password != null;
    }

    @Override
    public void addAuth(Map<String, String> headers, URI uri, byte[] body) {
        if (cachedAuthHeader == null) {
            cachedAuthHeader = buildAuthHeader();
        }
        if (cachedAuthHeader != null) {
            headers.put("Authorization", cachedAuthHeader);
        }
    }

    private String buildAuthHeader() {
        if (username == null || password == null) {
            return null;
        }
        String credentials = username + ":" + password;
        return "Basic " + Base64.encode(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
