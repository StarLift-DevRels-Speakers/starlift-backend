package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.LectureCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.LectureResponse;
import ru.shaxowskiy.javaspeakerclub.dto.LectureUpdateRequest;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.LecturesRecord;
import ru.shaxowskiy.javaspeakerclub.repository.LectureRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LectureService {

    private final LectureRepository lectureRepository;
    private final TalkRepository talkRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;

    @Transactional(readOnly = true)
    public List<LectureResponse> findByTalkId(UUID talkId) {
        talkRepository.findById(talkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + talkId));

        return lectureRepository.findByTalkId(talkId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LectureResponse> findBySpeakerId(Long speakerId) {
        userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        return lectureRepository.findBySpeakerId(speakerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LectureResponse findById(UUID id) {
        LecturesRecord record = lectureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + id));
        return toResponse(record);
    }

    @Transactional
    public LectureResponse create(LectureCreateRequest request) {
        var talk = talkRepository.findById(request.talkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Talk not found: " + request.talkId()));

        userRepository.findById(request.speakerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Speaker not found: " + request.speakerId()));

        requirePermissionForSpeaker(request.speakerId());
        if (hasRole(AppRole.SPEAKER) && !talk.getSpeakerId().equals(request.speakerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Speaker can add lectures only to own talks");
        }

        LecturesRecord record = lectureRepository.create(
                request.title(),
                request.talkId(),
                request.speakerId()
        );
        return toResponse(record);
    }

    @Transactional
    public LectureResponse update(UUID id, LectureUpdateRequest request) {
        LecturesRecord existing = lectureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + id));

        requirePermissionForSpeaker(existing.getSpeakerId());

        LecturesRecord updated = lectureRepository.update(id, request.title())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + id));
        return toResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        LecturesRecord existing = lectureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + id));

        requirePermissionForSpeaker(existing.getSpeakerId());

        if (!lectureRepository.deleteById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + id);
        }
    }

    @Transactional
    public LectureResponse uploadMedia(UUID lectureId, MultipartFile file) {
        LecturesRecord lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Lecture not found: " + lectureId));

        requirePermissionForSpeaker(lecture.getSpeakerId());

        String oldKey = lecture.getMediaS3Key();
        String objectKey = minioService.uploadFile(lecture.getId().toString(), file);

        LecturesRecord updated = lectureRepository.updateMediaS3Key(lecture.getId(), objectKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update media key for lecture: " + lecture.getId()));

        minioService.deleteFile(oldKey);

        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public String getMediaUrl(UUID lectureId) {
        LecturesRecord lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + lectureId));

        if (lecture.getMediaS3Key() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture has no media attached: " + lectureId);
        }

        return minioService.getPresignedUrl(lecture.getMediaS3Key());
    }

    @Transactional(readOnly = true)
    public MediaContent getMedia(UUID lectureId) {
        LecturesRecord lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + lectureId));

        String mediaKey = lecture.getMediaS3Key();
        if (mediaKey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture has no media attached: " + lectureId);
        }

        String filename = extractFilename(mediaKey);
        InputStream stream = minioService.getFile(mediaKey);
        return new MediaContent(filename, stream);
    }

    @Transactional
    public void deleteMedia(UUID lectureId) {
        LecturesRecord lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found: " + lectureId));

        requirePermissionForSpeaker(lecture.getSpeakerId());

        if (lecture.getMediaS3Key() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture has no media attached: " + lectureId);
        }

        String mediaKey = lecture.getMediaS3Key();
        lectureRepository.updateMediaS3Key(lectureId, null);
        minioService.deleteFile(mediaKey);
    }

    private LectureResponse toResponse(LecturesRecord record) {
        boolean hasMedia = record.getMediaS3Key() != null;
        String mediaFilename = hasMedia ? extractFilename(record.getMediaS3Key()) : null;
        String mediaMime = hasMedia ? minioService.getContentType(record.getMediaS3Key()) : null;
        return new LectureResponse(
                record.getId(),
                record.getTitle(),
                record.getTalkId(),
                record.getSpeakerId(),
                hasMedia,
                mediaFilename,
                mediaMime,
                record.getCreatedDate()
        );
    }

    private String extractFilename(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "download";
        }
        if (!objectKey.contains("/")) {
            return objectKey;
        }
        String name = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        int uuidSeparator = name.indexOf('_');
        return uuidSeparator > 0 ? name.substring(uuidSeparator + 1) : name;
    }

    public record MediaContent(String filename, InputStream stream) {}

    private void requirePermissionForSpeaker(Long speakerId) {
        if (hasRole(AppRole.ADMIN) || hasRole(AppRole.DEVREL)) {
            return;
        }
        if (hasRole(AppRole.SPEAKER)) {
            Long current = currentUserId();
            if (speakerId.equals(current)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not enough rights for this speaker");
    }

    private boolean hasRole(AppRole role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role.asAuthority()));
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
