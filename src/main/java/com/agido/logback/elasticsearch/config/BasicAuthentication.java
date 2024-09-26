package com.agido.logback.elasticsearch.config;

import com.agido.logback.elasticsearch.util.Base64;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;

public class BasicAuthentication implements Authentication {
    public void addAuth(HttpURLConnection urlConnection, String body) {
        String userInfo = urlConnection.getURL().getUserInfo();
        if (userInfo != null) {
            try {
                userInfo = URLDecoder.decode(userInfo,"UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            String basicAuth = "Basic " + Base64.encode(userInfo.getBytes());
            urlConnection.setRequestProperty("Authorization", basicAuth);
        }
    }
}
