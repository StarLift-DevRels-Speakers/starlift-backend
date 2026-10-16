package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.config.SharedDownloadProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SharedDownloadUrlService {

    private final SharedDownloadProperties sharedDownloadProperties;

    public String wrapPresignedUrl(String presignedUrl) {
        String baseUrl = sharedDownloadProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank() || presignedUrl == null || presignedUrl.isBlank()) {
            return null;
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(presignedUrl.getBytes(StandardCharsets.UTF_8));
        return normalizedBase + "/" + encoded;
    }
}
