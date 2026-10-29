package ru.shaxowskiy.javaspeakerclub.catalog;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.SpeakerTalkResponse;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.SpeakerFeedbackResponse;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackService;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.TalkFeedback;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Talks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserProfileTagRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;
import ru.shaxowskiy.javaspeakerclub.service.SpeakerTalkService;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Каталог спикеров для DevRel: публичный профиль, выступления, заготовки докладов и отзывы.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    /**
     * Значение users.region по умолчанию — «не заполнено».
     */
    private static final String DEFAULT_REGION = "Другие регионы";

    private final DSLContext dsl;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileTagRepository profileTagRepository;
    private final TalkRepository talkRepository;
    private final SpeakerTalkService speakerTalkService;
    private final FeedbackService feedbackService;

    public record CatalogSpeaker(
            Long id,
            String name,
            String position,
            String company,
            String city,
            String skills,
            String about,
            String preferences,
            int feedbackCount,
            int speechCount
    ) {
    }

    public record SpeechItem(UUID id, UUID talkId, String topic, String event, LocalDate date) {
    }

    public record CatalogSpeakerDetails(
            CatalogSpeaker profile,
            List<SpeechItem> speeches,
            List<SpeakerTalkResponse> talks,
            List<SpeakerFeedbackResponse> feedback
    ) {
    }

    /**
     * query ищет без учёта регистра по имени, компетенциям и городу.
     */
    @Transactional(readOnly = true)
    public List<CatalogSpeaker> search(String query) {
        List<Long> ids = userRoleRepository.findUserIdsByRole(AppRole.SPEAKER);
        Map<Long, Integer> feedbackCounts = countFeedback(ids);
        Map<Long, Integer> speechCounts = countSpeeches(ids);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByIds(ids).stream()
                .map(user -> toSpeaker(user, feedbackCounts.getOrDefault(user.getId(), 0), speechCounts.getOrDefault(user.getId(), 0)))
                .filter(speaker -> needle.isEmpty()
                        || (speaker.name() + " " + speaker.skills() + " " + speaker.city()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogSpeakerDetails details(Long speakerId) {
        if (!userRoleRepository.findRolesByUserId(speakerId).contains(AppRole.SPEAKER)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Спикер не найден");
        }
        UsersRecord user = userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Спикер не найден"));
        List<TalksRecord> talks = talkRepository.findBySpeakerId(speakerId);
        List<SpeakerFeedbackResponse> feedback = feedbackService.findBySpeakerId(speakerId);
        return new CatalogSpeakerDetails(
                toSpeaker(user, feedback.size(), talks.size()),
                talks.stream()
                        .map(talk -> new SpeechItem(
                                talk.getId(),
                                talk.getSpeakerTalkId(),
                                talk.getTopic(),
                                talk.getActivityName(),
                                talk.getTalkDate() == null ? null : talk.getTalkDate().toLocalDate()
                        ))
                        .toList(),
                speakerTalkService.findBySpeakerId(speakerId),
                feedback
        );
    }

    private CatalogSpeaker toSpeaker(UsersRecord user, int feedbackCount, int speechCount) {
        String name = Stream.of(user.getFirstName(), user.getLastName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        String city = user.getRegion() == null || DEFAULT_REGION.equals(user.getRegion()) ? "" : user.getRegion();
        return new CatalogSpeaker(
                user.getId(),
                name.isBlank() ? user.getUsername() : name,
                nullToEmpty(user.getCompanyRole()),
                nullToEmpty(user.getCompany()),
                city,
                String.join(", ", profileTagRepository.findByUserId(user.getId())),
                nullToEmpty(user.getAbout()),
                nullToEmpty(user.getPreferences()),
                feedbackCount,
                speechCount
        );
    }

    private Map<Long, Integer> countFeedback(List<Long> speakerIds) {
        if (speakerIds.isEmpty()) {
            return Map.of();
        }
        return dsl.select(Talks.TALKS.SPEAKER_ID, DSL.count())
                .from(TalkFeedback.TALK_FEEDBACK)
                .join(Talks.TALKS).on(Talks.TALKS.ID.eq(TalkFeedback.TALK_FEEDBACK.TALK_ID))
                .where(Talks.TALKS.SPEAKER_ID.in(speakerIds))
                .groupBy(Talks.TALKS.SPEAKER_ID)
                .fetchMap(Talks.TALKS.SPEAKER_ID, DSL.count());
    }

    private Map<Long, Integer> countSpeeches(List<Long> speakerIds) {
        if (speakerIds.isEmpty()) {
            return Map.of();
        }
        return dsl.select(Talks.TALKS.SPEAKER_ID, DSL.count())
                .from(Talks.TALKS)
                .where(Talks.TALKS.SPEAKER_ID.in(speakerIds))
                .groupBy(Talks.TALKS.SPEAKER_ID)
                .fetchMap(Talks.TALKS.SPEAKER_ID, DSL.count());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
