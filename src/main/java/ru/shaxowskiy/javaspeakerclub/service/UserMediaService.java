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
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UserMediaRecord;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerTalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserMediaRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserMediaService {

    private final UserMediaRepository userMediaRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final SpeakerTalkRepository speakerTalkRepository;

    public record MediaFileResponse(UUID id, String name, Long sizeBytes, UUID talkId, LocalDateTime createdDate) {
    }

    @Transactional
    public UserMediaUploadResponse upload(MultipartFile file) {
        return upload(file, null);
    }

    /**
     * talkId — заготовка доклада текущего спикера, к которой относится файл; может быть null.
     */
    @Transactional
    public UserMediaUploadResponse upload(MultipartFile file, UUID talkId) {
        Long userId = currentUserId();
        if (talkId != null && speakerTalkRepository.findById(talkId).filter(talk -> talk.getSpeakerId().equals(userId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Доклад не найден");
        }
        String folder = "user-media/" + userId;
        String objectKey = minioService.uploadFile(folder, file);
        UUID id = UUID.randomUUID();
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "file"
                : file.getOriginalFilename();
        try {
            userMediaRepository.create(id, userId, objectKey, name, file.getSize(), talkId);
        } catch (RuntimeException ex) {
            minioService.deleteFile(objectKey);
            throw ex;
        }
        return new UserMediaUploadResponse(id, objectKey, name, file.getSize(), talkId);
    }

    @Transactional(readOnly = true)
    public List<MediaFileResponse> findMine() {
        return userMediaRepository.findByUserId(currentUserId()).stream()
                .map(record -> new MediaFileResponse(
                        record.getId(),
                        record.getOriginalName() != null ? record.getOriginalName() : fileNameOf(record.getMediaMinioKey()),
                        record.getSizeBytes(),
                        record.getSpeakerTalkId(),
                        record.getCreatedDate()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getUrl(UUID id) {
        return minioService.getPresignedUrl(requireMine(id).getMediaMinioKey());
    }

    @Transactional
    public void delete(UUID id) {
        UserMediaRecord record = requireMine(id);
        userMediaRepository.deleteById(id);
        minioService.deleteFile(record.getMediaMinioKey());
    }

    private UserMediaRecord requireMine(UUID id) {
        return userMediaRepository.findByIdAndUserId(id, currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден"));
    }

    /**
     * Ключ объекта: folder/uuid_имя — для файлов, загруженных до сохранения имени.
     */
    private static String fileNameOf(String objectKey) {
        String last = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        int separator = last.indexOf('_');
        return separator >= 0 ? last.substring(separator + 1) : last;
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
