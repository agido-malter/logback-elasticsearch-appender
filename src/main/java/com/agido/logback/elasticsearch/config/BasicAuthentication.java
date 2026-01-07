package com.agido.logback.elasticsearch.config;

import com.agido.logback.elasticsearch.util.Base64;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class BasicAuthentication implements Authentication {
    
    private volatile String cachedAuthHeader;
    
    public void addAuth(HttpURLConnection urlConnection, String body) {
        if (cachedAuthHeader == null) {
            cachedAuthHeader = buildAuthHeader(urlConnection);
        }
        if (cachedAuthHeader != null) {
            urlConnection.setRequestProperty("Authorization", cachedAuthHeader);
        }
    }
    
    private String buildAuthHeader(HttpURLConnection urlConnection) {
        String userInfo = urlConnection.getURL().getUserInfo();
        if (userInfo == null) {
            return null;
        }
        try {
            userInfo = URLDecoder.decode(userInfo, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return "Basic " + Base64.encode(userInfo.getBytes(StandardCharsets.UTF_8));
    }
}
