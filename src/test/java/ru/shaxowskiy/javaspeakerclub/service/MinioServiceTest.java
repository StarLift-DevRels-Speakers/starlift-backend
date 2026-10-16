package ru.shaxowskiy.javaspeakerclub.service;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.shaxowskiy.javaspeakerclub.config.MinioConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class MinioServiceTest {

    private static final String SECRET_KEY = "test-secret-key";
    private static final String BUCKET = "lectures-media";
    private static final String OBJECT_KEY = "lecture-42/3f1c_slides.pdf";

    private final MockWebServer internalS3 = failFastServer();
    private final MockWebServer publicS3 = failFastServer();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinioConfig.class, MinioService.class)
            .withPropertyValues(
                    "s3.region=us-east-1",
                    "s3.access-key=test-access-key",
                    "s3.secret-key=" + SECRET_KEY,
                    "s3.bucket=" + BUCKET);

    @AfterEach
    void shutDownServers() throws IOException {
        internalS3.shutdown();
        publicS3.shutdown();
    }

    @Test
    void presignedUrlIsSignedForPublicEndpoint() {
        String publicEndpoint = publicS3.url("/").toString();

        contextRunner
                .withPropertyValues("s3.endpoint=" + internalS3.url("/"), "s3.public-endpoint=" + publicEndpoint)
                .run(context -> {
                    String url = context.getBean(MinioService.class).getPresignedUrl(OBJECT_KEY);

                    assertThat(url).startsWith(publicEndpoint + BUCKET + "/" + OBJECT_KEY + "?");
                    assertSignedForOwnHost(URI.create(url));
                });

        // Presigning must not ask the public host for the bucket location: the app often cannot reach it.
        assertThat(publicS3.getRequestCount()).isZero();
        assertThat(internalS3.getRequestCount()).isZero();
    }

    @Test
    void blankPublicEndpointFallsBackToInternalEndpoint() {
        String internalEndpoint = internalS3.url("/").toString();

        contextRunner
                .withPropertyValues("s3.endpoint=" + internalEndpoint, "s3.public-endpoint=")
                .run(context -> {
                    String url = context.getBean(MinioService.class).getPresignedUrl(OBJECT_KEY);

                    assertThat(url).startsWith(internalEndpoint + BUCKET + "/" + OBJECT_KEY + "?");
                    assertSignedForOwnHost(URI.create(url));
                });

        assertThat(internalS3.getRequestCount()).isZero();
    }

    /**
     * Presigning is local, so any request here is a bug: answer it with 404 at once instead of
     * blocking until the MinIO client times out.
     */
    private static MockWebServer failFastServer() {
        QueueDispatcher dispatcher = new QueueDispatcher();
        dispatcher.setFailFast(true);
        MockWebServer server = new MockWebServer();
        server.setDispatcher(dispatcher);
        return server;
    }

    /**
     * Recomputes the SigV4 query signature the way S3 checks it, over the host the URL points at.
     * A URL whose host was changed after signing fails here, as it does in MinIO.
     */
    private static void assertSignedForOwnHost(URI url) throws GeneralSecurityException {
        Map<String, String> params = Arrays.stream(url.getRawQuery().split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
        assertThat(params).containsEntry("X-Amz-SignedHeaders", "host");

        String canonicalQuery = params.entrySet().stream()
                .filter(param -> !param.getKey().equals("X-Amz-Signature"))
                .sorted(Map.Entry.comparingByKey())
                .map(param -> param.getKey() + "=" + param.getValue())
                .collect(Collectors.joining("&"));
        String canonicalRequest = String.join("\n",
                "GET", url.getRawPath(), canonicalQuery, "host:" + url.getRawAuthority(), "", "host", "UNSIGNED-PAYLOAD");

        // X-Amz-Credential is <access key>/<date>/<region>/s3/aws4_request
        String scope = URLDecoder.decode(params.get("X-Amz-Credential"), UTF_8).split("/", 2)[1];
        String stringToSign = String.join("\n",
                "AWS4-HMAC-SHA256",
                params.get("X-Amz-Date"),
                scope,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(UTF_8))));

        byte[] signingKey = ("AWS4" + SECRET_KEY).getBytes(UTF_8);
        for (String scopePart : scope.split("/")) {
            signingKey = hmacSha256(signingKey, scopePart);
        }
        assertThat(params.get("X-Amz-Signature")).isEqualTo(HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign)));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(UTF_8));
    }
}
