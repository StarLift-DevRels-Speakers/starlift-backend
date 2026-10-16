package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.TalkFeedbackCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.TalkFeedbackResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;
import ru.shaxowskiy.javaspeakerclub.repository.TalkFeedbackRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TalkFeedbackService {

    private final TalkFeedbackRepository talkFeedbackRepository;
    private final TalkRepository talkRepository;

    @Transactional(readOnly = true)
    public List<TalkFeedbackResponse> findAllByTalkId(UUID talkId) {
        requireTalkExists(talkId);
        return talkFeedbackRepository.findAllByTalkId(talkId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TalkFeedbackResponse findByIdAndTalkId(UUID id, UUID talkId) {
        requireTalkExists(talkId);
        TalkFeedbackRecord record = talkFeedbackRepository.findByIdAndTalkId(id, talkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk feedback not found: " + id));
        return toResponse(record);
    }

    @Transactional
    public TalkFeedbackResponse create(UUID talkId, TalkFeedbackCreateRequest request) {
        requireTalkExists(talkId);
        validateRequest(request);

        TalkFeedbackRecord record = talkFeedbackRepository.create(
                talkId,
                request.rating(),
                request.positive().trim(),
                request.negative().trim(),
                request.improvementSuggestions().trim()
        );
        return toResponse(record);
    }

    private TalkFeedbackResponse toResponse(TalkFeedbackRecord record) {
        return new TalkFeedbackResponse(
                record.getId(),
                record.getTalkId(),
                record.getRating(),
                record.getPositive(),
                record.getNegative(),
                record.getImprovementSuggestions(),
                record.getCreatedDate()
        );
    }

    private void requireTalkExists(UUID talkId) {
        talkRepository.findById(talkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talk not found: " + talkId));
    }

    private void validateRequest(TalkFeedbackCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.rating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating is required");
        }
        if (request.rating() < 0 || request.rating() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating must be between 0 and 10");
        }
        requireText(request.positive(), "positive");
        requireText(request.negative(), "negative");
        requireText(request.improvementSuggestions(), "improvementSuggestions");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
    }
}
