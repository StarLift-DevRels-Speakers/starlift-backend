package ru.shaxowskiy.javaspeakerclub.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.shaxowskiy.javaspeakerclub.config.S3Properties;
import ru.shaxowskiy.javaspeakerclub.exception.StorageException;
import ru.shaxowskiy.javaspeakerclub.exception.UnsupportedMediaTypeException;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime",
            "audio/mpeg", "audio/wav", "audio/ogg",
            "application/pdf",
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final MinioClient minioClient;
    private final S3Properties s3Properties;

    /**
     * Uploads file to MinIO bucket under {@code folder/uuid_originalFilename}.
     * Validates content type against an allowlist before uploading.
     *
     * @return the object key used to store the file
     */
    public String uploadFile(String folder, MultipartFile file) {
        validateContentType(file);

        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown";
        String objectKey = folder + "/" + UUID.randomUUID() + "_" + originalFilename;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(s3Properties.getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Uploaded file to MinIO: bucket={}, key={}", s3Properties.getBucket(), objectKey);
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to MinIO: " + objectKey, e);
        }

        return objectKey;
    }

    /**
     * Returns an InputStream to the stored object.
     * Caller is responsible for closing the returned stream.
     */
    public InputStream getFile(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(s3Properties.getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to get file from MinIO: " + objectKey, e);
        }
    }

    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(s3Properties.getBucket())
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to generate presigned URL for: " + objectKey, e);
        }
    }

    public String getContentType(String objectKey) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(s3Properties.getBucket())
                            .object(objectKey)
                            .build()
            ).contentType();
        } catch (Exception e) {
            throw new StorageException("Failed to fetch content type for: " + objectKey, e);
        }
    }

    public void deleteFile(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(s3Properties.getBucket())
                            .object(objectKey)
                            .build()
            );
            log.info("Deleted file from MinIO: bucket={}, key={}", s3Properties.getBucket(), objectKey);
        } catch (Exception e) {
            throw new StorageException("Failed to delete file from MinIO: " + objectKey, e);
        }
    }

    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedMediaTypeException(
                    "Unsupported file type: " + contentType
                            + ". Allowed types: " + ALLOWED_CONTENT_TYPES);
        }
    }
}
