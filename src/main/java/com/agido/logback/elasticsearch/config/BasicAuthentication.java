package com.agido.logback.elasticsearch.config;

import com.agido.logback.elasticsearch.util.Base64;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
    
    public void addAuth(HttpURLConnection urlConnection, String body) {
        if (cachedAuthHeader == null) {
            cachedAuthHeader = buildAuthHeader(urlConnection);
        }
        if (cachedAuthHeader != null) {
            urlConnection.setRequestProperty("Authorization", cachedAuthHeader);
        }
    }
    
    private String buildAuthHeader(HttpURLConnection urlConnection) {
        String credentials;
        
        // Prefer explicit username/password config
        if (username != null && password != null) {
            credentials = username + ":" + password;
        } else {
            // Fallback to URL userInfo (backward compatible)
            String userInfo = urlConnection.getURL().getUserInfo();
            if (userInfo == null) {
                return null;
            }
            try {
                credentials = URLDecoder.decode(userInfo, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        
        return "Basic " + Base64.encode(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
