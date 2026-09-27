package ru.shaxowskiy.javaspeakerclub.producer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerNpsHistoryRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerRolesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.Activity;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.CfpItem;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.FeedbackItem;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.FeedbackStats;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.Nps;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.Speaker;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.TalkItem;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository.SpeakerRecommendationRow;
import ru.shaxowskiy.javaspeakerclub.repository.RoleRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerNpsHistoryRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkFeedbackRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SpeakerSnapshotService {

    private static final List<String> STAGES = List.of("local", "regional", "federal", "international");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TalkRepository talkRepository;
    private final TalkFeedbackRepository talkFeedbackRepository;
    private final SpeakerNpsHistoryRepository speakerNpsHistoryRepository;
    private final CfpRecommendationRepository cfpRecommendationRepository;
    private final ProducerProperties properties;

    @Transactional(readOnly = true)
    public SpeakerSnapshot build(Long speakerId) {
        UsersRecord user = userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        List<TalksRecord> talks = talkRepository.findBySpeakerId(speakerId);
        List<TalkFeedbackRecord> feedback = talkFeedbackRepository
                .findBySpeakerIdSince(speakerId, now.minusDays(properties.feedbackWindowDays()))
                .stream()
                .limit(properties.maxFeedbackItems())
                .toList();

        return new SpeakerSnapshot(
                today,
                toSpeaker(user),
                toActivity(talks, now),
                toNps(user, speakerNpsHistoryRepository.findBySpeakerId(speakerId)),
                toTalkItems(talks),
                feedback.stream().map(this::toFeedbackItem).toList(),
                toFeedbackStats(feedback),
                toCfpItems(cfpRecommendationRepository.findBySpeakerId(speakerId), today)
        );
    }

    private Speaker toSpeaker(UsersRecord user) {
        String stage = normalizeStage(user.getInfluence());
        int index = STAGES.indexOf(stage);
        String nextStage = index + 1 < STAGES.size() ? STAGES.get(index + 1) : null;
        List<String> roles = roleRepository.findBySpeakerId(user.getId()).stream()
                .map(SpeakerRolesRecord::getName)
                .toList();
        return new Speaker(
                user.getId(),
                user.getUsername(),
                displayName(user),
                user.getCompany(),
                user.getCompanyRole(),
                user.getRegion(),
                roles,
                stage,
                nextStage
        );
    }

    private Activity toActivity(List<TalksRecord> talks, LocalDateTime now) {
        List<LocalDateTime> pastDates = talks.stream()
                .map(TalksRecord::getTalkDate)
                .filter(Objects::nonNull)
                .filter(date -> !date.isAfter(now))
                .toList();

        int last90 = countBetween(pastDates, now.minusDays(90), now);
        int previous90 = countBetween(pastDates, now.minusDays(180), now.minusDays(90));
        LocalDate lastTalkDate = pastDates.stream()
                .max(Comparator.naturalOrder())
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
        Integer daysSinceLastTalk = lastTalkDate == null
                ? null
                : (int) ChronoUnit.DAYS.between(lastTalkDate, now.toLocalDate());

        return new Activity(pastDates.size(), last90, previous90, lastTalkDate, daysSinceLastTalk, momentum(last90, previous90));
    }

    static String momentum(int last90, int previous90) {
        if (last90 == 0 && previous90 == 0) {
            return "inactive";
        }
        if (last90 > previous90) {
            return "growing";
        }
        if (last90 < previous90) {
            return "declining";
        }
        return "stable";
    }

    private int countBetween(List<LocalDateTime> dates, LocalDateTime fromExclusive, LocalDateTime toInclusive) {
        return (int) dates.stream()
                .filter(date -> date.isAfter(fromExclusive) && !date.isAfter(toInclusive))
                .count();
    }

    private Nps toNps(UsersRecord user, List<SpeakerNpsHistoryRecord> historyNewestFirst) {
        Integer current = toNps100(user.getNps());
        Integer previous = historyNewestFirst.size() >= 2 ? toNps100(historyNewestFirst.get(1).getNps()) : null;
        Integer delta = current != null && previous != null ? current - previous : null;
        return new Nps(current, previous, delta);
    }

    private List<TalkItem> toTalkItems(List<TalksRecord> talks) {
        List<TalksRecord> recent = talks.stream()
                .sorted(Comparator.comparing(TalksRecord::getTalkDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(properties.recentTalksLimit())
                .toList();
        Map<UUID, List<String>> tags = talkRepository.findTagsByTalkIds(recent.stream().map(TalksRecord::getId).toList());
        return recent.stream()
                .map(talk -> new TalkItem(
                        talk.getId().toString(),
                        talk.getTopic(),
                        talk.getTalkDate() == null ? null : talk.getTalkDate().toLocalDate(),
                        talk.getActivityName(),
                        talk.getConferenceType(),
                        talk.getFormat(),
                        tags.getOrDefault(talk.getId(), List.of())
                ))
                .toList();
    }

    private FeedbackItem toFeedbackItem(TalkFeedbackRecord record) {
        return new FeedbackItem(
                record.getId().toString(),
                record.getTalkId().toString(),
                record.getRating(),
                record.getPositive(),
                record.getNegative(),
                record.getImprovementSuggestions(),
                record.getCreatedDate() == null ? null : record.getCreatedDate().toLocalDate()
        );
    }

    private FeedbackStats toFeedbackStats(List<TalkFeedbackRecord> feedback) {
        if (feedback.isEmpty()) {
            return new FeedbackStats(0, null, null, null, null);
        }
        int count = feedback.size();
        double average = feedback.stream().mapToInt(TalkFeedbackRecord::getRating).average().orElse(0);
        int promoters = percent(feedback.stream().filter(item -> item.getRating() >= 9).count(), count);
        int detractors = percent(feedback.stream().filter(item -> item.getRating() <= 6).count(), count);
        return new FeedbackStats(
                count,
                BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                promoters,
                detractors,
                promoters - detractors
        );
    }

    private List<CfpItem> toCfpItems(List<SpeakerRecommendationRow> rows, LocalDate today) {
        List<SpeakerRecommendationRow> open = rows.stream()
                .filter(row -> row.recommendation().getDeadline() != null)
                .filter(row -> !row.recommendation().getDeadline().isBefore(today))
                .toList();
        Map<UUID, List<String>> tags = cfpRecommendationRepository.findTagsByRecommendationIds(
                open.stream().map(row -> row.recommendation().getId()).toList()
        );
        return open.stream()
                .map(row -> new CfpItem(
                        row.recommendation().getId().toString(),
                        row.recommendation().getTitle(),
                        row.recommendation().getCity(),
                        normalizeStage(row.recommendation().getInfluenceLevel()),
                        row.recommendation().getTopic(),
                        row.recommendation().getDeadline(),
                        row.matchScore() == null ? 0 : row.matchScore(),
                        tags.getOrDefault(row.recommendation().getId(), List.of()),
                        row.recommendation().getUrl()
                ))
                .toList();
    }

    private static int percent(long part, int total) {
        return Math.round(part * 100f / total);
    }

    private static Integer toNps100(BigDecimal nps0to10) {
        if (nps0to10 == null) {
            return null;
        }
        return nps0to10.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static String normalizeStage(String influence) {
        String value = influence == null ? "" : influence.toLowerCase(Locale.ROOT);
        return STAGES.contains(value) ? value : "local";
    }

    private static String displayName(UsersRecord user) {
        String full = Stream.of(user.getFirstName(), user.getLastName(), user.getSurName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return full.isBlank() ? user.getUsername() : full;
    }
}
