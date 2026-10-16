package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerRunsRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingProperties;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.ExpertApi.PreferencesRequest;
import ru.shaxowskiy.javaspeakerclub.producer.matching.engine.CfpMatcher;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventRecommendationResult;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventRecommendationResult.Event;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Interactions;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation.Group;
import ru.shaxowskiy.javaspeakerclub.producer.matching.profile.ExpertProfileBuilder;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.MatchingDataService.PlatformData;
import ru.shaxowskiy.javaspeakerclub.repository.ExpertPreferencesRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ExpertPreferencesRepository.PreferencesInput;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository.NewInteraction;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerRunRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerRunRepository.NewRun;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сценарий эксперта (US-10 — US-13): профиль с видами экспертизы и пробелами, предпочтения и подбор мероприятий
 * и CFP — из каталога и открытых организаторами платформы.
 */
@Service
@RequiredArgsConstructor
public class ExpertRecommendationService {

    private final MatchingDataService dataService;
    private final ExpertPreferencesRepository preferencesRepository;
    private final RecommendationSummaryWriter summaryWriter;
    private final ProducerRunRepository runRepository;
    private final ProducerInteractionRepository interactionRepository;
    private final ProducerJson json;
    private final MatchingProperties properties;

    public record RunLog(EventRecommendationResult response, List<CfpMatcher.Diagnostics> diagnostics,
                         RecommendationSummaryWriter.Report summaries) {
    }

    public ExpertProfile profile(Long userId) {
        PlatformData data = dataService.load(userId);
        return ExpertProfileBuilder.build(dataService.expert(data, userId), data.cooccurrence(), data.vocabulary(), LocalDate.now());
    }

    public ExpertProfile savePreferences(Long userId, PreferencesRequest request) {
        preferencesRepository.upsert(userId, new PreferencesInput(
                join(request.languages()),
                join(request.formats()),
                request.onlineOk(),
                request.offlineOk(),
                join(request.interests()),
                request.limitations() == null || request.limitations().isBlank() ? null : request.limitations().trim()));
        return profile(userId);
    }

    public EventRecommendationResult recommend(Long userId) {
        LocalDate today = LocalDate.now();
        PlatformData data = dataService.load(userId);
        ExpertFacts facts = dataService.expert(data, userId);
        ExpertProfile profile = ExpertProfileBuilder.build(facts, data.cooccurrence(), data.vocabulary(), today);
        CfpMatcher.Outcome outcome = CfpMatcher.match(facts, profile, data.cfps(), today, properties.maxEvents());

        RecommendationSummaryWriter.Result summaries = summaryWriter.write(RecommendationSummaryWriter.AUDIENCE_EXPERT,
                summaryInputs(outcome.groups()));
        List<Group<Event>> groups = outcome.groups().stream()
                .map(group -> group.withItems(group.items().stream()
                        .map(event -> {
                            String summary = summaries.summaries().get(event.cfpId());
                            return summary == null ? event : event.withAiSummary(summary);
                        })
                        .toList()))
                .toList();

        ProducerRunsRecord run = runRepository.insert(new NewRun(
                Interactions.SCENARIO_EXPERT_EVENTS,
                userId,
                String.valueOf(userId),
                json.write(Map.of("expertise", profile.expertise().stream().map(ExpertProfile.ExpertiseArea::area).toList(),
                        "gaps", profile.gaps().stream().map(ExpertProfile.Gap::field).toList())),
                json.write(data.cfps().stream().map(CfpMatcher.CfpFact::id).toList()),
                null,
                summaries.report().model(),
                properties.algorithmVersion() + (summaries.report().attempted() ? "+" + properties.summaryPromptVersion() : "")));
        EventRecommendationResult result = new EventRecommendationResult(
                groups,
                profile.gaps().stream().map(ExpertProfile.Gap::message).toList(),
                outcome.notes(),
                outcome.considered(),
                userId,
                run.getId().toString(),
                LocalDateTime.now());
        runRepository.updateResult(run.getId(), json.write(new RunLog(result, outcome.diagnostics(), summaries.report())));

        Map<String, CfpMatcher.CfpFact> cfps = data.cfps().stream()
                .collect(Collectors.toMap(CfpMatcher.CfpFact::id, cfp -> cfp, (left, right) -> left));
        for (Group<Event> group : groups) {
            for (Event event : group.items()) {
                CfpMatcher.CfpFact cfp = cfps.get(event.cfpId());
                interactionRepository.insert(new NewInteraction(run.getId(), userId,
                        event.eventId() == null ? null : UUID.fromString(event.eventId()),
                        UUID.fromString(event.cfpId()), userId, Interactions.RECOMMENDED, Interactions.KIND_AI,
                        cfp == null ? null : cfp.topic(), cfp == null ? null : cfp.talkFormat(), group.title(),
                        event.confidence().level()));
            }
        }
        return result;
    }

    static List<RecommendationSummaryWriter.Input> summaryInputs(List<Group<Event>> groups) {
        List<RecommendationSummaryWriter.Input> inputs = new ArrayList<>();
        for (Group<Event> group : groups) {
            for (Event event : group.items()) {
                List<String> facts = new ArrayList<>();
                facts.add("Группа: " + group.title() + ".");
                facts.add("Дедлайн CFP через " + event.cfp().daysLeft() + " дн.");
                event.whyFits().forEach(fact -> facts.add(RecommendationSummaryWriter.PLUS + fact));
                event.limitations().forEach(fact -> facts.add(RecommendationSummaryWriter.LIMITATION + fact));
                facts.add("Уверенность: " + event.confidence().level() + ".");
                inputs.add(new RecommendationSummaryWriter.Input(event.cfpId(), event.title(), facts, event.relevantExperience()));
            }
        }
        return inputs;
    }

    private static String join(List<String> values) {
        if (values == null) {
            return null;
        }
        String joined = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.replace(",", " "))
                .distinct()
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }
}
