package com.agido.logback.elasticsearch.config;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * This class implements Amazon AWS v4 Signature signing for ElasticSearch,
 * using the AWS SDK v2.
 *
 * @author blagerweij
 */
public class AWSAuthentication implements Authentication {

    private final Aws4Signer signer;
    private final AwsCredentialsProvider credentialsProvider;
    private final String region;
    private final Clock clock;

    public AWSAuthentication() {
        this(DefaultCredentialsProvider.create(), resolveRegion(), Clock.systemUTC());
    }

    /**
     * Package-private constructor for testing: allows injecting credentials, region and clock.
     */
    AWSAuthentication(AwsCredentialsProvider credentialsProvider, Region region, Clock clock) {
        this.signer = Aws4Signer.create();
        this.credentialsProvider = credentialsProvider;
        this.region = region != null ? region.id() : null;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    private static Region resolveRegion() {
        try {
            return new DefaultAwsRegionProviderChain().getRegion();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void addAuth(Map<String, String> headers, URI uri, byte[] body) {
        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(uri)
                .contentStreamProvider(() -> new ByteArrayInputStream(body))
                // The "required" token makes the Aws4Signer compute the payload hash, substitute it
                // here, and include x-amz-content-sha256 in the SignedHeaders set.
                .putHeader("x-amz-content-sha256", "required")
                .build();

        Aws4SignerParams params = Aws4SignerParams.builder()
                .awsCredentials(credentialsProvider.resolveCredentials())
                .signingRegion(region != null ? Region.of(region) : null)
                .signingName("es")
                .signingClockOverride(clock)
                .build();

        SdkHttpFullRequest signed = signer.sign(request, params);

        copyHeader(signed, headers, "Authorization");
        copyHeader(signed, headers, "X-Amz-Date");
        copyHeader(signed, headers, "X-Amz-Content-Sha256");
        copyHeader(signed, headers, "X-Amz-Security-Token");
    }

    private void copyHeader(SdkHttpFullRequest signed, Map<String, String> headers, String name) {
        for (Map.Entry<String, List<String>> entry : signed.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                headers.put(name, entry.getValue().get(0));
                return;
            }
        }
    }
}
