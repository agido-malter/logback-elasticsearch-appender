package com.agido.logback.elasticsearch.config;

import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AWSAuthenticationTest {

    // Independent, well-known SHA-256 of the ASCII string "test".
    private static final String SHA256_OF_TEST =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    // Golden SigV4 signature captured deterministically with the fixed clock + static credentials
    // below. Pinned so future regressions in the signing path are caught.
    private static final String GOLDEN_SIGNATURE =
            "66cbb29c90ba6e397674b479a7092648b16d51eef2a0e0edded09c97ec2fc63f";

    @Test
    public void should_produce_deterministic_sigv4_signature() {
        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"));
        Clock fixed = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

        AWSAuthentication auth = new AWSAuthentication(creds, Region.US_EAST_1, fixed);

        Map<String, String> headers = new LinkedHashMap<>();
        auth.addAuth(headers,
                URI.create("https://search-mydomain.us-east-1.es.amazonaws.com/_bulk"),
                "test".getBytes(UTF_8));

        // x-amz-content-sha256 must equal the independent known SHA-256 of "test".
        assertThat(headers.get("X-Amz-Content-Sha256"), is(SHA256_OF_TEST));

        assertTrue("X-Amz-Date should start with 20240101 but was " + headers.get("X-Amz-Date"),
                headers.get("X-Amz-Date").startsWith("20240101"));

        String authHeader = headers.get("Authorization");
        String pattern = "^AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20240101/us-east-1/es/aws4_request, "
                + "SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=[0-9a-f]{64}$";
        assertTrue("Authorization header did not match expected SigV4 shape: " + authHeader,
                authHeader.matches(pattern));

        // Golden equality assertion (pinned signature).
        assertThat(authHeader, is("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20240101/us-east-1/es/aws4_request, "
                + "SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=" + GOLDEN_SIGNATURE));
    }

    @Test
    public void should_sign_for_opensearch_serverless_when_aoss_is_configured() {
        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"));
        Clock fixed = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

        AWSAuthentication auth = new AWSAuthentication(creds, Region.US_EAST_1, fixed);
        auth.setServiceName("aoss");

        Map<String, String> headers = new LinkedHashMap<>();
        auth.addAuth(headers,
                URI.create("https://collection-id.us-east-1.aoss.amazonaws.com/_bulk"),
                "test".getBytes(UTF_8));

        assertThat(auth.getServiceName(), is("aoss"));
        assertTrue(headers.get("Authorization").contains(
                "Credential=AKIDEXAMPLE/20240101/us-east-1/aoss/aws4_request"));
        assertThat(headers.get("X-Amz-Content-Sha256"), is(SHA256_OF_TEST));
    }

    @Test
    public void should_reject_blank_service_name() {
        AWSAuthentication auth = new AWSAuthentication();

        assertThrows(IllegalArgumentException.class, () -> auth.setServiceName("  "));
    }
}
