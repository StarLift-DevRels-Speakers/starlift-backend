package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.UserMediaUploadResponse;
import ru.shaxowskiy.javaspeakerclub.repository.UserMediaRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserMediaService {

    private final UserMediaRepository userMediaRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;

    @Transactional
    public UserMediaUploadResponse upload(MultipartFile file) {
        Long userId = currentUserId();
        String folder = "user-media/" + userId;
        String objectKey = minioService.uploadFile(folder, file);
        UUID id = UUID.randomUUID();
        try {
            userMediaRepository.create(id, userId, objectKey);
        } catch (RuntimeException ex) {
            minioService.deleteFile(objectKey);
            throw ex;
        }
        return new UserMediaUploadResponse(id, objectKey);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
