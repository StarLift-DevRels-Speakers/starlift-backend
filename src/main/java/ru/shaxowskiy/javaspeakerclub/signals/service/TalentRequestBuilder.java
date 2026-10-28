package ru.shaxowskiy.javaspeakerclub.signals.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ParsingEventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerAchievementsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository.SpeakerRecommendationRow;
import ru.shaxowskiy.javaspeakerclub.repository.ParsingEventRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerAchievementRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkFeedbackRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserProfileTagRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.signals.WeakSignalsProperties;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest.AchievementItem;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest.FeedbackItem;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest.ParsingEventItem;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest.TalkItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Собирает тело запроса в weak-signals строго из базы. Если данных нет — null, без подстановки нулей.
 * Исключения — avg_rating и achievements_progress_pct: weak-signals пока не принимает для них null.
 */
@Service
@RequiredArgsConstructor
public class TalentRequestBuilder {

    /**
     * Значения по умолчанию колонок users.region и users.company — это «не заполнено», а не реальные регион и компания.
     */
    static final String DEFAULT_REGION = "Другие регионы";
    static final String DEFAULT_COMPANY = "Unknown";

    /**
     * Подтверждённые события уже превращены в доклады (talks), отклонённые и дубликаты — шум.
     */
    static final String PARSING_STATUS_NEW = "new";

    /**
     * Границы заглушки для achievements_progress_pct: 3–7, умноженные на 10 (шкала 0–100%).
     */
    static final double PLACEHOLDER_MIN = 3.0;
    static final double PLACEHOLDER_MAX = 7.0;

    private final UserRepository userRepository;
    private final TalkRepository talkRepository;
    private final TalkFeedbackRepository talkFeedbackRepository;
    private final SpeakerAchievementRepository achievementRepository;
    private final CfpRecommendationRepository cfpRecommendationRepository;
    private final ParsingEventRepository parsingEventRepository;
    private final UserProfileTagRepository profileTagRepository;
    private final WeakSignalsProperties properties;

    @Transactional(readOnly = true)
    public TalentRequest build(Long speakerId) {
        UsersRecord user = userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        LocalDateTime now = LocalDateTime.now();
        List<TalksRecord> pastTalks = pastTalksNewestFirst(talkRepository.findBySpeakerId(speakerId), now);
        List<TalkFeedbackRecord> feedback = talkFeedbackRepository.findBySpeakerId(speakerId);
        List<SpeakerAchievementsRecord> achievements = achievementRepository.findBySpeakerId(speakerId);

        return new TalentRequest(
                user.getId(),
                user.getUsername(),
                filledOrNull(user.getRegion(), DEFAULT_REGION),
                filledOrNull(user.getCompany(), DEFAULT_COMPANY),
                blankToNull(user.getCompanyRole()),
                blankToNull(user.getAbout()),
                user.getInfluence() == null ? null : user.getInfluence().toLowerCase(Locale.ROOT),

                pastTalks.size(),
                (int) pastTalks.stream().filter(talk -> talk.getTalkDate().isAfter(now.minusMonths(3))).count(),
                pastTalks.isEmpty() ? null : pastTalks.getFirst().getTalkDate(),
                averageRating(feedback),
                feedback.size(),
                user.getNps(),
                user.getProfileCompletion(),
                achievementsProgressPct(achievements),
                cfpHighMatchCount(cfpRecommendationRepository.findBySpeakerId(speakerId), now.toLocalDate()),

                pastTalks.stream().limit(properties.recentTalksLimit()).map(TalentRequestBuilder::toTalkItem).toList(),
                feedback.stream().limit(properties.recentFeedbackLimit()).map(TalentRequestBuilder::toFeedbackItem).toList(),
                achievements.stream().limit(properties.achievementsLimit()).map(TalentRequestBuilder::toAchievementItem).toList(),
                newParsingEvents(parsingEventRepository.findBySpeakerId(speakerId)),
                profileTagRepository.findByUserId(speakerId)
        );
    }

    private static List<TalksRecord> pastTalksNewestFirst(List<TalksRecord> talks, LocalDateTime now) {
        return talks.stream()
                .filter(talk -> talk.getTalkDate() != null && !talk.getTalkDate().isAfter(now))
                .sorted(Comparator.comparing(TalksRecord::getTalkDate).reversed())
                .toList();
    }

    private static double averageRating(List<TalkFeedbackRecord> feedback) {
        if (feedback.isEmpty()) {
            // Отсутствие отзывов — валидное состояние, его видно по feedback_count = 0.
            return 0.0;
        }
        double average = feedback.stream().mapToInt(TalkFeedbackRecord::getRating).average().orElseThrow();
        return round(average, 2);
    }

    /**
     * Средний прогресс по целям; перевыполненная цель считается как 100%.
     */
    private static double achievementsProgressPct(List<SpeakerAchievementsRecord> achievements) {
        if (achievements.isEmpty()) {
            // TODO: передавать null, когда weak-signals начнёт принимать achievements_progress_pct: float | None.
            //  Сейчас целей на фронте нет вообще, поэтому без целей шлём случайные 30–70%.
            return placeholder(1) * 10;
        }
        double average = achievements.stream()
                .mapToDouble(item -> Math.min(item.getCurrentValue() / (double) item.getTargetValue(), 1.0) * 100)
                .average()
                .orElseThrow();
        return round(average, 1);
    }

    /**
     * Активные CFP с открытым дедлайном и совпадением не ниже порога.
     */
    private int cfpHighMatchCount(List<SpeakerRecommendationRow> rows, LocalDate today) {
        return (int) rows.stream()
                .filter(row -> row.matchScore() != null && row.matchScore() >= properties.cfpHighMatchThreshold())
                .filter(row -> row.recommendation().getDeadline() == null || !row.recommendation().getDeadline().isBefore(today))
                .count();
    }

    private List<ParsingEventItem> newParsingEvents(List<ParsingEventsRecord> events) {
        return events.stream()
                .filter(event -> PARSING_STATUS_NEW.equalsIgnoreCase(event.getStatus()))
                .sorted(Comparator.comparing(ParsingEventsRecord::getEventDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(properties.parsingEventsLimit())
                .map(event -> new ParsingEventItem(
                        event.getConference(),
                        blankToNull(event.getCompany()),
                        event.getEventDate(),
                        blankToNull(event.getNote())
                ))
                .toList();
    }

    private static TalkItem toTalkItem(TalksRecord talk) {
        return new TalkItem(talk.getTopic(), talk.getConferenceType(), talk.getFormat(), talk.getTalkDate());
    }

    private static FeedbackItem toFeedbackItem(TalkFeedbackRecord record) {
        return new FeedbackItem(
                record.getRating(),
                record.getPositive(),
                record.getNegative(),
                record.getImprovementSuggestions()
        );
    }

    private static AchievementItem toAchievementItem(SpeakerAchievementsRecord record) {
        return new AchievementItem(
                record.getTitle(),
                record.getDescription(),
                record.getCurrentValue(),
                record.getTargetValue()
        );
    }

    /**
     * Временная заглушка для achievements_progress_pct, пока целей нет в базе: случайное число 3–7.
     */
    static double placeholder(int scale) {
        return round(ThreadLocalRandom.current().nextDouble(PLACEHOLDER_MIN, PLACEHOLDER_MAX), scale);
    }

    private static String filledOrNull(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return Objects.equals(normalized, defaultValue) ? null : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
