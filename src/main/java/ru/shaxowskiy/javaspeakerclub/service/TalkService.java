package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.LectureResponse;
import ru.shaxowskiy.javaspeakerclub.dto.TalkCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.TalkResponse;
import ru.shaxowskiy.javaspeakerclub.dto.TalkUpdateRequest;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TalkService {

    private final TalkRepository talkRepository;
    private final UserRepository userRepository;
    private final LectureService lectureService;

    @Transactional(readOnly = true)
    public List<TalkResponse> findAll() {
        return toResponses(talkRepository.findAll());
    }

    @Transactional(readOnly = true)
    public TalkResponse findById(UUID id) {
        TalksRecord record = talkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + id));
        return toResponse(record, talkRepository.findTagsByTalkId(id));
    }

    @Transactional(readOnly = true)
    public List<TalkResponse> findByConferenceType(String conferenceType) {
        return toResponses(talkRepository.findByConferenceType(conferenceType));
    }

    @Transactional
    public TalkResponse create(TalkCreateRequest request) {
        userRepository.findById(request.speakerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Speaker not found: " + request.speakerId()));

        requirePermissionForSpeaker(request.speakerId());

        TalksRecord record = talkRepository.create(
                request.speakerId(),
                request.topic(),
                request.talkDate(),
                request.conferenceType(),
                request.format(),
                request.activityName(),
                request.activityDate()
        );
        if (request.tags() != null) {
            talkRepository.replaceTags(record.getId(), request.tags());
        }
        return toResponse(record, talkRepository.findTagsByTalkId(record.getId()));
    }

    @Transactional
    public TalkResponse update(UUID id, TalkUpdateRequest request) {
        return mergeAndUpdate(id, request);
    }

    @Transactional
    public TalkResponse patch(UUID id, TalkUpdateRequest request) {
        return mergeAndUpdate(id, request);
    }

    @Transactional
    public TalkResponse addTags(UUID talkId, List<String> tags) {
        TalksRecord existing = talkRepository.findById(talkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + talkId));
        requirePermissionForSpeaker(existing.getSpeakerId());

        talkRepository.addTags(talkId, tags);
        return toResponse(existing, talkRepository.findTagsByTalkId(talkId));
    }

    @Transactional
    public TalkResponse removeTag(UUID talkId, String tag) {
        TalksRecord existing = talkRepository.findById(talkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + talkId));
        requirePermissionForSpeaker(existing.getSpeakerId());

        talkRepository.deleteTag(talkId, tag);
        return toResponse(existing, talkRepository.findTagsByTalkId(talkId));
    }

    @Transactional
    public void delete(UUID id) {
        TalksRecord existing = talkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + id));

        requirePermissionForSpeaker(existing.getSpeakerId());

        if (!talkRepository.deleteById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + id);
        }
    }

    private List<TalkResponse> toResponses(List<TalksRecord> talks) {
        Map<UUID, List<String>> tagsByTalk = talkRepository.findTagsByTalkIds(
                talks.stream().map(TalksRecord::getId).toList()
        );
        return talks.stream()
                .map(record -> toResponse(record, tagsByTalk.getOrDefault(record.getId(), List.of())))
                .toList();
    }

    private TalkResponse toResponse(TalksRecord record, List<String> tags) {
        UsersRecord speaker = userRepository.findById(record.getSpeakerId()).orElse(null);

        List<LectureResponse> lectures = lectureService.findByTalkId(record.getId());

        return new TalkResponse(
                record.getId(),
                record.getSpeakerId(),
                speaker == null ? null : speaker.getUsername(),
                speaker == null ? null : speaker.getFirstName(),
                speaker == null ? null : speaker.getLastName(),
                speaker == null ? null : speaker.getSurName(),
                record.getTopic(),
                record.getTalkDate(),
                record.getConferenceType(),
                record.getFormat(),
                record.getActivityName(),
                record.getActivityDate(),
                record.getCreatedDate(),
                tags,
                lectures
        );
    }

    private TalkResponse mergeAndUpdate(UUID id, TalkUpdateRequest request) {
        TalksRecord existing = talkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + id));

        requirePermissionForSpeaker(existing.getSpeakerId());

        TalksRecord updated = talkRepository.update(
                id,
                request.topic() != null ? request.topic() : existing.getTopic(),
                request.talkDate() != null ? request.talkDate() : existing.getTalkDate(),
                request.conferenceType() != null ? request.conferenceType() : existing.getConferenceType(),
                request.format() != null ? request.format() : existing.getFormat(),
                request.activityName() != null ? request.activityName() : existing.getActivityName(),
                request.activityDate() != null ? request.activityDate() : existing.getActivityDate()
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + id));

        if (request.tags() != null) {
            talkRepository.replaceTags(id, request.tags());
        }

        return toResponse(updated, talkRepository.findTagsByTalkId(id));
    }

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
                .map(UsersRecord::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
