package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.TalkFeedbackCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.TalkFeedbackResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.repository.TalkFeedbackRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TalkFeedbackService {

    private static final String AUTHOR_PARTICIPANT = "participant";
    private static final List<String> AUTHOR_TYPES = List.of(AUTHOR_PARTICIPANT, "organizer", "other");

    private final TalkFeedbackRepository talkFeedbackRepository;
    private final TalkRepository talkRepository;
    private final ApplicationEventPublisher eventPublisher;

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
        TalksRecord talk = requireTalkExists(talkId);
        validateRequest(request);

        TalkFeedbackRecord record = talkFeedbackRepository.create(
                talkId,
                request.rating(),
                request.positive().trim(),
                request.negative().trim(),
                request.improvementSuggestions().trim(),
                resolveAuthorType(request.authorType())
        );
        eventPublisher.publishEvent(new GamificationEvents.FeedbackCreated(
                record.getId(), talkId, talk.getSpeakerId(), record.getRating()));
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
                record.getCreatedDate(),
                record.getAuthorType()
        );
    }

    /**
     * Тип автора влияет на вес отзыва в подборе AI-продюсера, поэтому отзыв организатора
     * может оставить только DevRel или администратор; анонимный отзыв — всегда отзыв участника.
     */
    private String resolveAuthorType(String requested) {
        String type = requested == null || requested.isBlank() ? AUTHOR_PARTICIPANT : requested.trim().toLowerCase(Locale.ROOT);
        if (!AUTHOR_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authorType must be one of " + AUTHOR_TYPES);
        }
        if (!AUTHOR_PARTICIPANT.equals(type) && !isDevRelOrAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only DevRel or admin can leave " + type + " feedback");
        }
        return type;
    }

    private static boolean isDevRelOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> AppRole.DEVREL.asAuthority().equals(authority) || AppRole.ADMIN.asAuthority().equals(authority));
    }

    private TalksRecord requireTalkExists(UUID talkId) {
        return talkRepository.findById(talkId)
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
