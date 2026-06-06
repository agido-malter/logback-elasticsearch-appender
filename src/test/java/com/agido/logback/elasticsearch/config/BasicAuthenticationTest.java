package com.agido.logback.elasticsearch.config;

import org.junit.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class BasicAuthenticationTest {

    @Test
    public void should_encode_credentials_with_special_characters_using_explicit_username_password() {
        // Given: explicit username/password with special characters
        BasicAuthentication auth = new BasicAuthentication();
        auth.setUsername("user");
        auth.setPassword("p@ss€wörd#123");

        Map<String, String> headers = new LinkedHashMap<>();

        // When
        auth.addAuth(headers, URI.create("http://localhost:9200/_bulk"), new byte[0]);

        // Then: verify credentials are encoded with UTF-8
        String expectedCredentials = "user:p@ss€wörd#123";
        String expectedBase64 = Base64.getEncoder().encodeToString(expectedCredentials.getBytes(StandardCharsets.UTF_8));
        assertThat(headers.get("Authorization"), is("Basic " + expectedBase64));
    }

    @Test
    public void should_use_url_encoded_userinfo_decoded_the_way_the_writer_does() {
        // The writer strips userInfo from the URL, URL-decodes it (UTF-8) and feeds the
        // username/password setters. This test reproduces that decode step for the legacy form
        // http://user:p%40ss%E2%82%ACw%C3%B6rd%23123@localhost:9200 and asserts the resulting header.
        String rawUserInfo = "user:p%40ss%E2%82%ACw%C3%B6rd%23123";
        String decoded = URLDecoder.decode(rawUserInfo, StandardCharsets.UTF_8);
        int idx = decoded.indexOf(':');

        BasicAuthentication auth = new BasicAuthentication();
        auth.setUsername(decoded.substring(0, idx));
        auth.setPassword(decoded.substring(idx + 1));

        Map<String, String> headers = new LinkedHashMap<>();

        // When
        auth.addAuth(headers, URI.create("http://localhost:9200/_bulk"), new byte[0]);

        // Then
        String expectedCredentials = "user:p@ss€wörd#123";
        String expectedBase64 = Base64.getEncoder().encodeToString(expectedCredentials.getBytes(StandardCharsets.UTF_8));
        assertThat(headers.get("Authorization"), is("Basic " + expectedBase64));
    }
}
