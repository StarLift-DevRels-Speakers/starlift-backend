package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.SpeakerTalkRequest;
import ru.shaxowskiy.javaspeakerclub.dto.SpeakerTalkResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerTalksRecord;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerTalkRepository;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.util.List;
import java.util.UUID;

/**
 * Заготовки докладов текущего спикера.
 */
@Service
@RequiredArgsConstructor
public class SpeakerTalkService {

    private final SpeakerTalkRepository speakerTalkRepository;
    private final SpeakerAccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<SpeakerTalkResponse> findMine() {
        return findBySpeakerId(accessGuard.currentUserId());
    }

    @Transactional(readOnly = true)
    public List<SpeakerTalkResponse> findBySpeakerId(Long speakerId) {
        return speakerTalkRepository.findBySpeakerId(speakerId).stream()
                .map(SpeakerTalkService::toResponse)
                .toList();
    }

    @Transactional
    public SpeakerTalkResponse create(SpeakerTalkRequest request) {
        Input input = validate(request);
        return toResponse(speakerTalkRepository.create(accessGuard.currentUserId(), input.name(), input.topic(), input.description()));
    }

    @Transactional
    public SpeakerTalkResponse update(UUID id, SpeakerTalkRequest request) {
        requireOwn(id);
        Input input = validate(request);
        return speakerTalkRepository.update(id, input.name(), input.topic(), input.description())
                .map(SpeakerTalkService::toResponse)
                .orElseThrow(SpeakerTalkService::notFound);
    }

    /**
     * Заготовка текущего спикера или 404: чужие заготовки не раскрываются.
     */
    @Transactional(readOnly = true)
    public SpeakerTalksRecord requireOwn(UUID id) {
        return requireOfSpeaker(id, accessGuard.currentUserId());
    }

    @Transactional(readOnly = true)
    public SpeakerTalksRecord requireOfSpeaker(UUID id, Long speakerId) {
        SpeakerTalksRecord record = speakerTalkRepository.findById(id).orElseThrow(SpeakerTalkService::notFound);
        if (!record.getSpeakerId().equals(speakerId)) {
            throw notFound();
        }
        return record;
    }

    private static Input validate(SpeakerTalkRequest request) {
        String name = request == null ? null : trimToNull(request.name());
        String topic = request == null ? null : trimToNull(request.topic());
        if (name == null || topic == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите название и тему");
        }
        String description = trimToNull(request.description());
        return new Input(name, topic, description == null ? "" : description);
    }

    private static SpeakerTalkResponse toResponse(SpeakerTalksRecord record) {
        return new SpeakerTalkResponse(
                record.getId(),
                record.getSpeakerId(),
                record.getName(),
                record.getTopic(),
                record.getDescription(),
                record.getCreatedDate()
        );
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Доклад не найден");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Input(String name, String topic, String description) {
    }
}
