package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerRunsRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingProperties;
import ru.shaxowskiy.javaspeakerclub.producer.matching.analysis.EventCardAssembler;
import ru.shaxowskiy.javaspeakerclub.producer.matching.engine.SpeakerMatcher;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Interactions;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation.Group;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.SpeakerMatchResult;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.SpeakerMatchResult.Candidate;
import ru.shaxowskiy.javaspeakerclub.producer.matching.profile.ExpertProfileBuilder;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.MatchingDataService.PlatformData;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository.NewInteraction;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerRunRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerRunRepository.NewRun;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Подбор спикеров под мероприятие (US-04 — US-07). Отбирает и объясняет код (SpeakerMatcher), модель только
 * пересказывает объяснение первых кандидатов. Каждый запуск пишется в журнал, каждая рекомендация — в историю как
 * вывод ИИ (kind = ai), а не как факт.
 */
@Service
@RequiredArgsConstructor
public class EventSpeakerSelectionService {

    private final EventRequestService eventService;
    private final MatchingDataService dataService;
    private final RecommendationSummaryWriter summaryWriter;
    private final ProducerRunRepository runRepository;
    private final ProducerInteractionRepository interactionRepository;
    private final ProducerJson json;
    private final MatchingProperties properties;

    /** Что хранится в журнале: ответ пользователю, внутренние коэффициенты и итог проверки пересказов. */
    public record RunLog(SpeakerMatchResult response, List<SpeakerMatcher.Diagnostics> diagnostics,
                         RecommendationSummaryWriter.Report summaries) {
    }

    public SpeakerMatchResult match(ProducerActor actor, UUID eventId) {
        EventRequestService.Loaded loaded = eventService.load(actor, eventId);
        EventCard card = loaded.card();
        LocalDate today = LocalDate.now();

        PlatformData data = dataService.load(null);
        List<ExpertFacts> experts = data.experts().values().stream()
                .filter(facts -> facts.id() != loaded.record().getOrganizerId())
                .toList();
        Map<Long, ExpertProfile> profiles = new LinkedHashMap<>();
        experts.forEach(facts -> profiles.put(facts.id(),
                ExpertProfileBuilder.build(facts, data.cooccurrence(), data.vocabulary(), today)));

        SpeakerMatcher.Outcome outcome = SpeakerMatcher.match(card, experts, profiles, today, properties.maxCandidates());
        RecommendationSummaryWriter.Result summaries = summaryWriter.write(RecommendationSummaryWriter.AUDIENCE_ORGANIZER,
                summaryInputs(outcome.groups()));
        List<Group<Candidate>> groups = withSummaries(outcome.groups(), summaries.summaries());

        UUID runId = UUID.randomUUID();
        SpeakerMatchResult result = new SpeakerMatchResult(EventRequestService.displayTitle(card), groups, outcome.excluded(),
                outcome.notes(), outcome.eventSummary(), outcome.considered(), card.id(), null, LocalDateTime.now());
        ProducerRunsRecord run = runRepository.insert(new NewRun(
                Interactions.SCENARIO_SPEAKER_MATCH,
                actor.userId(),
                card.id(),
                json.write(Map.of("params", EventCardAssembler.snapshot(card), "criteria", EventCardAssembler.criteriaSnapshot(card))),
                json.write(experts.stream().map(ExpertFacts::id).toList()),
                null,
                summaries.report().model(),
                properties.algorithmVersion() + (summaries.report().attempted() ? "+" + properties.summaryPromptVersion() : "")));
        result = withRunId(result, run.getId().toString());
        runRepository.updateResult(run.getId(), json.write(new RunLog(result, outcome.diagnostics(), summaries.report())));

        String topic = card.value(EventParamKey.TOPICS);
        String format = card.value(EventParamKey.EVENT_FORMAT);
        for (Group<Candidate> group : groups) {
            for (Candidate candidate : group.items()) {
                interactionRepository.insert(new NewInteraction(run.getId(), candidate.expertId(), UUID.fromString(card.id()), null,
                        actor.userId(), Interactions.RECOMMENDED, Interactions.KIND_AI, topic, format, group.title(),
                        candidate.confidence().level()));
            }
        }
        eventService.markMatched(loaded);
        return result;
    }

    public SpeakerMatchResult latest(ProducerActor actor, UUID eventId) {
        eventService.load(actor, eventId);
        return latestRun(eventId)
                .map(RunLog::response)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Подбор ещё не запускался. Вызовите POST, чтобы подобрать спикеров."));
    }

    /** Последний подбор по мероприятию — для объяснения в приглашении спикеру. */
    public Optional<RunLog> latestRun(UUID eventId) {
        return runRepository.findLatest(Interactions.SCENARIO_SPEAKER_MATCH, eventId.toString())
                .filter(run -> run.getResultJson() != null)
                .map(run -> json.read(run.getResultJson(), RunLog.class));
    }

    static List<RecommendationSummaryWriter.Input> summaryInputs(List<Group<Candidate>> groups) {
        List<RecommendationSummaryWriter.Input> inputs = new ArrayList<>();
        for (Group<Candidate> group : groups) {
            for (Candidate candidate : group.items()) {
                List<String> facts = new ArrayList<>();
                facts.add("Группа: " + group.title() + ".");
                if (candidate.role() != null) {
                    facts.add("Роль: " + candidate.role() + ".");
                }
                candidate.whyFits().forEach(fact -> facts.add(RecommendationSummaryWriter.PLUS + fact));
                candidate.limitations().forEach(fact -> facts.add(RecommendationSummaryWriter.LIMITATION + fact));
                candidate.missingData().forEach(fact -> facts.add(RecommendationSummaryWriter.MISSING + fact));
                facts.add("Уверенность: " + candidate.confidence().level() + ".");
                inputs.add(new RecommendationSummaryWriter.Input(String.valueOf(candidate.expertId()), candidate.name(), facts,
                        candidate.relevantTalks()));
            }
        }
        return inputs;
    }

    private static List<Group<Candidate>> withSummaries(List<Group<Candidate>> groups, Map<String, String> summaries) {
        if (summaries.isEmpty()) {
            return groups;
        }
        return groups.stream()
                .map(group -> group.withItems(group.items().stream()
                        .map(candidate -> {
                            String summary = summaries.get(String.valueOf(candidate.expertId()));
                            return summary == null ? candidate : candidate.withAiSummary(summary);
                        })
                        .toList()))
                .toList();
    }

    private static SpeakerMatchResult withRunId(SpeakerMatchResult result, String runId) {
        return new SpeakerMatchResult(result.eventTitle(), result.groups(), result.excluded(), result.notes(), result.eventSummary(),
                result.consideredCount(), result.eventId(), runId, result.generatedAt());
    }
}
