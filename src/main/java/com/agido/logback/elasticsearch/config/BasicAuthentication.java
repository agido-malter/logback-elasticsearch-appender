package com.agido.logback.elasticsearch.config;

import com.agido.logback.elasticsearch.util.Base64;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class BasicAuthentication implements Authentication {
    public void addAuth(HttpURLConnection urlConnection, String body) {
        String userInfo = urlConnection.getURL().getUserInfo();
        if (userInfo != null) {
            try {
                userInfo = URLDecoder.decode(userInfo, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            String basicAuth = "Basic " + Base64.encode(userInfo.getBytes(StandardCharsets.UTF_8));
            urlConnection.setRequestProperty("Authorization", basicAuth);
        }
    }
}
