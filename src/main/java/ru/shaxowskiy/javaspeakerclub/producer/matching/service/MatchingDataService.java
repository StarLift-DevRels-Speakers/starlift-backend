package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.CfpRecommendationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ExpertPreferencesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerInteractionsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingProperties;
import ru.shaxowskiy.javaspeakerclub.producer.matching.engine.CfpMatcher.CfpFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.CfpHistory;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.FeedbackFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.Preferences;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.Signals;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.TalkFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.profile.TagCooccurrence;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ExpertPreferencesRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkFeedbackRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserProfileTagRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Собирает факты об экспертах из данных платформы одним проходом: профиль, доклады с тегами, отзывы с источником,
 * предпочтения и фактическую историю взаимодействий. Рекомендации AI (kind = ai) в факты не попадают.
 */
@Service
@RequiredArgsConstructor
public class MatchingDataService {

    private static final List<String> STAGES = List.of("local", "regional", "federal", "international");
    private static final List<String> CFP_ACTIONS = List.of("viewed_event", "opened_cfp", "applied", "application_accepted",
            "application_rejected", "application_reserve", "marked_irrelevant");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileTagRepository profileTagRepository;
    private final TalkRepository talkRepository;
    private final TalkFeedbackRepository feedbackRepository;
    private final ExpertPreferencesRepository preferencesRepository;
    private final ProducerInteractionRepository interactionRepository;
    private final CfpRecommendationRepository cfpRepository;
    private final MatchingProperties properties;

    /** Всё, что нужно подбору: эксперты, связи тегов, словарь тегов платформы и открытые CFP. */
    public record PlatformData(
            Map<Long, ExpertFacts> experts,
            TagCooccurrence cooccurrence,
            List<String> vocabulary,
            List<CfpFact> cfps
    ) {
    }

    /** Эксперты — пользователи с ролью SPEAKER; extraUserId добавляется, даже если роли нет (свой профиль). */
    @Transactional(readOnly = true)
    public PlatformData load(Long extraUserId) {
        Set<Long> ids = new LinkedHashSet<>(userRoleRepository.findUserIdsByRole(AppRole.SPEAKER));
        if (extraUserId != null) {
            ids.add(extraUserId);
        }
        List<Long> expertIds = List.copyOf(ids);
        List<UsersRecord> users = userRepository.findByIds(expertIds).stream()
                .sorted(Comparator.comparing(UsersRecord::getId))
                .toList();

        List<TalksRecord> talks = talkRepository.findBySpeakerIds(expertIds);
        Map<UUID, List<String>> talkTags = talkRepository.findTagsByTalkIds(talks.stream().map(TalksRecord::getId).toList());
        Map<UUID, List<FeedbackFact>> feedbackByTalk = feedbackRepository.findByTalkIds(talks.stream().map(TalksRecord::getId).toList())
                .stream()
                .map(MatchingDataService::toFeedback)
                .collect(Collectors.groupingBy(item -> UUID.fromString(item.talkId()), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<String>> profileTags = profileTagRepository.findByUserIds(expertIds);
        Map<Long, ExpertPreferencesRecord> preferences = preferencesRepository.findByUserIds(expertIds);
        Map<Long, List<ProducerInteractionsRecord>> facts = interactionRepository.findFactsByExpertIds(expertIds).stream()
                .collect(Collectors.groupingBy(ProducerInteractionsRecord::getExpertId));

        LocalDateTime now = LocalDateTime.now();
        Map<Long, List<TalkFact>> talksBySpeaker = new HashMap<>();
        for (TalksRecord talk : talks) {
            // Запланированный доклад ещё не подтверждает опыт.
            if (talk.getTalkDate() != null && talk.getTalkDate().isAfter(now)) {
                continue;
            }
            talksBySpeaker.computeIfAbsent(talk.getSpeakerId(), key -> new ArrayList<>()).add(new TalkFact(
                    talk.getId().toString(),
                    talk.getTopic(),
                    talk.getTalkDate() == null ? null : talk.getTalkDate().toLocalDate(),
                    talk.getActivityName(),
                    talk.getConferenceType(),
                    talk.getFormat(),
                    talkTags.getOrDefault(talk.getId(), List.of()).stream().map(TermMatcher::normalizeTag).toList()));
        }

        Map<Long, ExpertFacts> experts = new LinkedHashMap<>();
        for (UsersRecord user : users) {
            List<TalkFact> own = talksBySpeaker.getOrDefault(user.getId(), List.of());
            List<FeedbackFact> feedback = own.stream()
                    .flatMap(talk -> feedbackByTalk.getOrDefault(UUID.fromString(talk.id()), List.of()).stream())
                    .toList();
            experts.put(user.getId(), new ExpertFacts(
                    user.getId(),
                    user.getUsername(),
                    displayName(user),
                    user.getCompany(),
                    user.getCompanyRole(),
                    user.getRegion(),
                    normalizeStage(user.getInfluence()),
                    user.getAbout(),
                    profileTags.getOrDefault(user.getId(), List.of()),
                    toPreferences(preferences.get(user.getId())),
                    own,
                    feedback,
                    toSignals(facts.getOrDefault(user.getId(), List.of()))));
        }

        List<CfpsRecordWithTags> cfps = activeCfps();
        List<List<String>> tagSets = new ArrayList<>();
        talksBySpeaker.values().forEach(list -> list.forEach(talk -> tagSets.add(talk.tags())));
        cfps.forEach(cfp -> tagSets.add(cfp.tags()));

        return new PlatformData(experts, TagCooccurrence.from(tagSets), vocabulary(), cfps.stream().map(CfpsRecordWithTags::toFact).toList());
    }

    public ExpertFacts expert(PlatformData data, Long userId) {
        ExpertFacts facts = data.experts().get(userId);
        if (facts == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert not found: " + userId);
        }
        return facts;
    }

    /** Теги платформы — общий словарь для разбора описаний и для профиля эксперта. */
    @Transactional(readOnly = true)
    public List<String> vocabulary() {
        Map<String, String> tags = new LinkedHashMap<>();
        Stream.of(talkRepository.findDistinctTags(), cfpRepository.findDistinctTags(), profileTagRepository.findDistinctTags())
                .flatMap(List::stream)
                .filter(tag -> tag != null && !tag.isBlank())
                .forEach(tag -> tags.putIfAbsent(TermMatcher.normalizeTag(tag), TermMatcher.normalizeTag(tag)));
        return tags.values().stream().limit(properties.vocabularyLimit()).toList();
    }

    private record CfpsRecordWithTags(CfpRecommendationsRecord record, List<String> tags) {
        CfpFact toFact() {
            return new CfpFact(record.getId().toString(), record.getTitle(), record.getCity(), normalizeStage(record.getInfluenceLevel()),
                    record.getTopic(), record.getDeadline(), record.getHostCompany(), record.getUrl(), tags,
                    record.getDescription(), record.getTalkFormat(), record.getLanguage(), record.getAudienceLevel(),
                    record.getAttendance(), record.getTalkDuration(), record.getRequirements(),
                    record.getEventRequestId() == null ? null : record.getEventRequestId().toString());
        }
    }

    private List<CfpsRecordWithTags> activeCfps() {
        List<CfpRecommendationsRecord> records = cfpRepository.findActive();
        Map<UUID, List<String>> tags = cfpRepository.findTagsByRecommendationIds(records.stream().map(CfpRecommendationsRecord::getId).toList());
        return records.stream()
                .map(record -> new CfpsRecordWithTags(record, tags.getOrDefault(record.getId(), List.of()).stream()
                        .map(TermMatcher::normalizeTag).toList()))
                .toList();
    }

    private static FeedbackFact toFeedback(TalkFeedbackRecord record) {
        return new FeedbackFact(
                record.getId().toString(),
                record.getTalkId().toString(),
                record.getRating() == null ? 0 : record.getRating(),
                record.getPositive(),
                record.getNegative(),
                record.getImprovementSuggestions(),
                record.getAuthorType(),
                record.getCreatedDate() == null ? null : record.getCreatedDate().toLocalDate());
    }

    static Preferences toPreferences(ExpertPreferencesRecord record) {
        if (record == null) {
            return Preferences.empty();
        }
        return new Preferences(split(record.getLanguages()), split(record.getFormats()), record.getOnlineOk(),
                record.getOfflineOk(), split(record.getInterests()), blankToNull(record.getLimitations()));
    }

    /**
     * Сигналы из фактов: отказ и отклонённая заявка — не негатив (п. 24 ТЗ), поэтому считаются только положительные
     * исходы; пометка организатора «не подходит» действует только для его мероприятия.
     */
    static Signals toSignals(List<ProducerInteractionsRecord> facts) {
        int accepted = 0;
        int talksGiven = 0;
        int repeat = 0;
        Set<String> irrelevant = new LinkedHashSet<>();
        Map<String, CfpHistory> cfpHistory = new LinkedHashMap<>();
        for (ProducerInteractionsRecord fact : facts) {
            switch (fact.getAction()) {
                case "invitation_accepted" -> accepted++;
                case "talk_given" -> talksGiven++;
                case "repeat_invitation" -> repeat++;
                default -> {
                }
            }
            if ("marked_irrelevant".equals(fact.getAction()) && fact.getEventRequestId() != null) {
                irrelevant.add(fact.getEventRequestId().toString());
            }
            if (fact.getCfpId() != null && CFP_ACTIONS.contains(fact.getAction())) {
                cfpHistory.put(fact.getCfpId().toString(), new CfpHistory(fact.getAction(),
                        fact.getCreatedDate() == null ? null : fact.getCreatedDate().toLocalDate()));
            }
        }
        return new Signals(accepted, talksGiven, repeat, irrelevant, cfpHistory);
    }

    static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String normalizeStage(String influence) {
        String value = influence == null ? "" : influence.toLowerCase(Locale.ROOT);
        return STAGES.contains(value) ? value : "local";
    }

    static String displayName(UsersRecord user) {
        String full = Stream.of(user.getFirstName(), user.getLastName(), user.getSurName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return full.isBlank() ? user.getUsername() : full;
    }

    static LocalDate today() {
        return LocalDate.now();
    }
}
