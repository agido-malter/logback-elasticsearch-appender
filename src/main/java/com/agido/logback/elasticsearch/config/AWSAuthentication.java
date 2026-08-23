package com.agido.logback.elasticsearch.config;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * This class implements Amazon AWS v4 Signature signing for Amazon OpenSearch
 * Service, using the AWS SDK v2.
 *
 * @author blagerweij
 */
public class AWSAuthentication implements Authentication {

    private static final String DEFAULT_SERVICE_NAME = "es";

    private final AwsV4HttpSigner signer;
    private final AwsCredentialsProvider credentialsProvider;
    private final String region;
    private final Clock clock;
    private String serviceName = DEFAULT_SERVICE_NAME;

    public AWSAuthentication() {
        this(DefaultCredentialsProvider.builder().build(), resolveRegion(), Clock.systemUTC());
    }

    /**
     * Package-private constructor for testing: allows injecting credentials, region and clock.
     */
    AWSAuthentication(AwsCredentialsProvider credentialsProvider, Region region, Clock clock) {
        this.signer = AwsV4HttpSigner.create();
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
        SdkHttpRequest request = SdkHttpRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(uri)
                .build();

        SignedRequest signed = signer.sign(signRequest -> signRequest
                .identity(credentialsProvider.resolveCredentials())
                .request(request)
                .payload(ContentStreamProvider.fromByteArray(body))
                .putProperty(AwsV4HttpSigner.REGION_NAME, region)
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, serviceName)
                .putProperty(AwsV4HttpSigner.PAYLOAD_SIGNING_ENABLED, true)
                .putProperty(HttpSigner.SIGNING_CLOCK, clock));

        copyHeader(signed.request(), headers, "Authorization");
        copyHeader(signed.request(), headers, "X-Amz-Date");
        copyHeader(signed.request(), headers, "X-Amz-Content-Sha256");
        copyHeader(signed.request(), headers, "X-Amz-Security-Token");
    }

    /**
     * Sets the AWS SigV4 service name. The default {@code es} is used by
     * provisioned Amazon OpenSearch Service domains. Amazon OpenSearch
     * Serverless collections require {@code aoss}.
     */
    public void setServiceName(String serviceName) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        this.serviceName = serviceName.trim();
    }

    public String getServiceName() {
        return serviceName;
    }

    private void copyHeader(SdkHttpRequest signed, Map<String, String> headers, String name) {
        for (Map.Entry<String, List<String>> entry : signed.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                headers.put(name, entry.getValue().get(0));
                return;
            }
        }
    }
}
