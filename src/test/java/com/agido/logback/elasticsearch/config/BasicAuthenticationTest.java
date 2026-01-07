package com.agido.logback.elasticsearch.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BasicAuthenticationTest {

    @Test
    public void should_encode_credentials_with_special_characters_using_utf8() throws Exception {
        // Given: URL with special characters in password (e.g., @, €, ö, #)
        URL testUrl = new URL("http://user:p%40ss%E2%82%ACw%C3%B6rd%23123@localhost:9200");
        
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getURL()).thenReturn(testUrl);

        BasicAuthentication auth = new BasicAuthentication();

        // When
        auth.addAuth(mockConnection, "");

        // Then: verify credentials are encoded with UTF-8
        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        verify(mockConnection).setRequestProperty(eq("Authorization"), headerValue.capture());

        String expectedCredentials = "user:p@ss€wörd#123";
        String expectedBase64 = Base64.getEncoder().encodeToString(expectedCredentials.getBytes(StandardCharsets.UTF_8));
        assertThat(headerValue.getValue(), is("Basic " + expectedBase64));
    }
}
