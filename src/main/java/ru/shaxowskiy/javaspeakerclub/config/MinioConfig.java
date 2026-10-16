package ru.shaxowskiy.javaspeakerclub.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(S3Properties.class)
public class MinioConfig {

    private final S3Properties s3Properties;

    @Bean
    @Primary
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(s3Properties.getEndpoint())
                .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                .build();
    }

    /**
     * Signs presigned URLs for {@code s3.public-endpoint}, the address clients outside the app network use.
     * SigV4 signs the host, so a URL signed for {@code s3.endpoint} cannot be rewritten to another host.
     * The fixed region keeps presigning offline: without it the client asks the public host for the
     * bucket location, and that host is usually unreachable from inside the app container.
     */
    @Bean
    public MinioClient presignMinioClient() {
        String publicEndpoint = s3Properties.getPublicEndpoint();
        return MinioClient.builder()
                .endpoint(publicEndpoint == null || publicEndpoint.isBlank()
                        ? s3Properties.getEndpoint()
                        : publicEndpoint)
                .region(s3Properties.getRegion())
                .credentials(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                .build();
    }
}
