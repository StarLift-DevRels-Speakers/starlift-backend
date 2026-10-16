package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventRequestsRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.producer.llm.PromptFiles;
import ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingProperties;
import ru.shaxowskiy.javaspeakerclub.producer.matching.analysis.EventCardAssembler;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.CriterionRequest;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.EventCreateRequest;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.EventListItem;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.EventUpdateRequest;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventState;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Interactions;
import ru.shaxowskiy.javaspeakerclub.repository.EventRequestRepository;
import ru.shaxowskiy.javaspeakerclub.repository.EventRequestRepository.EventRequestInput;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerRunRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerRunRepository.NewRun;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Мероприятие организатора (US-01 — US-03, US-07): создание из текста и/или формы, уточняющие вопросы и правки.
 * Модель вызывается только для разбора описания; всё остальное — поля организатора и его ответы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRequestService {

    static final String TAGS_KEY = "tags";

    private final EventRequestRepository repository;
    private final ProducerRunRepository runRepository;
    private final MatchingDataService dataService;
    private final ProducerLlmClient llmClient;
    private final ProducerJson json;
    private final MatchingProperties properties;

    /** Сохранённое мероприятие вместе с разобранным состоянием и собранной карточкой. */
    public record Loaded(EventRequestsRecord record, EventState state, EventCard card) {
    }

    private record Analysis(EventAnalysisDraft draft, List<String> notes, String model) {
    }

    public EventCard create(ProducerActor actor, EventCreateRequest request) {
        String title = trimToNull(request.title());
        String description = trimToNull(request.description());
        Map<String, List<String>> structured = structured(request.params());
        if (request.tags() != null && !request.tags().isEmpty()) {
            structured.put(TAGS_KEY, request.tags().stream().filter(Objects::nonNull).map(String::trim).toList());
        }
        if (title == null && description == null && structured.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Опишите мероприятие текстом или заполните хотя бы один параметр");
        }
        List<String> vocabulary = dataService.vocabulary();
        Analysis analysis = analyze(title, description, vocabulary);
        EventState state = new EventState(structured, new LinkedHashMap<>(), new LinkedHashSet<>(), criteria(request.criteria()),
                new LinkedHashSet<>(), analysis.draft(), analysis.notes(), null, null);
        EventRequestsRecord record = repository.insert(new EventRequestInput(actor.userId(), title, description, json.write(state),
                EventCard.STATUS_ANALYZED, analysis.model(), analysis.model() == null ? null : properties.analysisPromptVersion()));
        EventCard card = assemble(record, state, vocabulary);
        runRepository.insert(new NewRun(Interactions.SCENARIO_EVENT_ANALYSIS, actor.userId(), record.getId().toString(),
                json.write(request), null, json.write(card), analysis.model(),
                analysis.model() == null ? null : properties.analysisPromptVersion()));
        return card;
    }

    public EventCard update(ProducerActor actor, UUID id, EventUpdateRequest request) {
        Loaded loaded = load(actor, id);
        EventRequestsRecord current = loaded.record();
        String title = request.title() == null ? current.getTitle() : trimToNull(request.title());
        String description = current.getDescription();
        EventState state = loaded.state();
        String model = current.getAnalysisModel();
        String promptVersion = current.getAnalysisPromptVersion();
        List<String> vocabulary = dataService.vocabulary();

        if (request.description() != null && !Objects.equals(trimToNull(request.description()), description)) {
            description = trimToNull(request.description());
            Analysis analysis = analyze(title, description, vocabulary);
            state = state.withDraft(analysis.draft(), analysis.notes());
            model = analysis.model();
            promptVersion = model == null ? null : properties.analysisPromptVersion();
        }

        Map<String, List<String>> structured = new LinkedHashMap<>(state.structured());
        if (request.params() != null) {
            request.params().forEach((key, values) -> {
                requireKey(key);
                List<String> clean = values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim)
                        .filter(value -> !value.isEmpty()).toList();
                if (clean.isEmpty()) {
                    structured.remove(key);
                } else {
                    structured.put(key, clean);
                }
            });
        }
        if (request.tags() != null) {
            structured.put(TAGS_KEY, request.tags().stream().filter(Objects::nonNull).map(String::trim).toList());
        }

        Map<String, List<String>> answers = new LinkedHashMap<>(state.answers());
        Set<String> skipped = new LinkedHashSet<>(state.skipped());
        if (request.answers() != null) {
            request.answers().forEach((key, answer) -> {
                requireQuestion(key);
                if (answer == null || answer.isBlank()) {
                    answers.remove(key);
                } else {
                    answers.put(key, List.of(answer.trim()));
                    skipped.remove(key);
                }
            });
        }
        if (request.skip() != null) {
            request.skip().forEach(key -> {
                requireQuestion(key);
                if (!answers.containsKey(key)) {
                    skipped.add(key);
                }
            });
        }

        List<EventState.CriterionInput> organizerCriteria = new ArrayList<>(state.organizerCriteria());
        organizerCriteria.addAll(criteria(request.addCriteria()));
        Set<String> rejected = new LinkedHashSet<>(state.rejectedCriteria());
        if (request.removeCriteria() != null) {
            for (String criterionId : request.removeCriteria()) {
                boolean own = organizerCriteria.removeIf(input -> EventCardAssembler.criterionId(input.text()).equals(criterionId));
                if (!own) {
                    rejected.add(criterionId);
                }
            }
        }

        EventState updated = new EventState(structured, answers, skipped, organizerCriteria, rejected, state.draft(),
                state.analysisNotes(), state.matchedParams(), state.matchedCriteria());
        EventRequestsRecord record = repository.update(id, new EventRequestInput(current.getOrganizerId(), title, description,
                json.write(updated), current.getStatus(), model, promptVersion));
        return assemble(record, updated, vocabulary);
    }

    public EventCard get(ProducerActor actor, UUID id) {
        return load(actor, id).card();
    }

    public List<EventListItem> list(ProducerActor actor) {
        List<String> vocabulary = dataService.vocabulary();
        List<EventRequestsRecord> records = actor.admin() ? repository.findAll() : repository.findByOrganizerId(actor.userId());
        return records.stream()
                .map(record -> {
                    EventCard card = assemble(record, json.read(record.getStateJson(), EventState.class), vocabulary);
                    int open = (int) card.questions().stream()
                            .filter(question -> EventCardAssembler.QUESTION_OPEN.equals(question.status()))
                            .count();
                    return new EventListItem(card.id(), displayTitle(card), card.status(), open, card.updatedDate());
                })
                .toList();
    }

    /** Загружает мероприятие с проверкой: организатор видит только свои, администратор — все. */
    public Loaded load(ProducerActor actor, UUID id) {
        EventRequestsRecord record = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Мероприятие не найдено: " + id));
        if (!actor.admin() && !record.getOrganizerId().equals(actor.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это мероприятие другого организатора");
        }
        EventState state = json.read(record.getStateJson(), EventState.class);
        return new Loaded(record, state, assemble(record, state, dataService.vocabulary()));
    }

    /** Без проверки владельца: для спикера, которого пригласили на мероприятие. */
    public Loaded loadForInvitation(UUID id) {
        EventRequestsRecord record = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Мероприятие не найдено: " + id));
        EventState state = json.read(record.getStateJson(), EventState.class);
        return new Loaded(record, state, assemble(record, state, dataService.vocabulary()));
    }

    public void markMatched(Loaded loaded) {
        EventState state = loaded.state().withMatched(EventCardAssembler.snapshot(loaded.card()),
                EventCardAssembler.criteriaSnapshot(loaded.card()));
        EventRequestsRecord record = loaded.record();
        repository.update(record.getId(), new EventRequestInput(record.getOrganizerId(), record.getTitle(), record.getDescription(),
                json.write(state), EventCard.STATUS_MATCHED, record.getAnalysisModel(), record.getAnalysisPromptVersion()));
    }

    static String displayTitle(EventCard card) {
        if (card.title() != null && !card.title().isBlank()) {
            return card.title();
        }
        List<String> topics = card.values(EventParamKey.TOPICS);
        return topics.isEmpty() ? "Мероприятие без названия" : "Мероприятие: " + String.join(", ", topics);
    }

    private EventCard assemble(EventRequestsRecord record, EventState state, List<String> vocabulary) {
        return EventCardAssembler.assemble(record.getId(), record.getTitle(), record.getDescription(), record.getStatus(), state,
                vocabulary, properties.maxQuestions(), record.getUpdatedDate());
    }

    private Analysis analyze(String title, String description, List<String> vocabulary) {
        if (description == null) {
            return new Analysis(null, List.of(), null);
        }
        String user = "Сегодня: " + LocalDate.now() + ".\n"
                + "Теги платформы: " + String.join(", ", vocabulary) + ".\n"
                + "Название: " + (title == null ? "не указано" : title) + ".\n"
                + "Описание:\n" + description;
        try {
            EventAnalysisDraft draft = llmClient.generate(PromptFiles.load(properties.analysisPromptVersion()), user,
                    EventAnalysisDraft.class);
            return new Analysis(draft, List.of(), llmClient.modelName());
        } catch (RuntimeException e) {
            log.warn("Event analysis failed: {}", e.getMessage());
            return new Analysis(null, List.of("Описание не удалось разобрать автоматически: карточка собрана из полей формы. "
                    + "Заполните параметры или повторите разбор, изменив описание."), null);
        }
    }

    private static Map<String, List<String>> structured(Map<String, List<String>> params) {
        Map<String, List<String>> structured = new LinkedHashMap<>();
        if (params == null) {
            return structured;
        }
        params.forEach((key, values) -> {
            requireKey(key);
            List<String> clean = values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim)
                    .filter(value -> !value.isEmpty()).toList();
            if (!clean.isEmpty()) {
                structured.put(key, clean);
            }
        });
        return structured;
    }

    private static List<EventState.CriterionInput> criteria(List<CriterionRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .filter(Objects::nonNull)
                .filter(request -> request.text() != null && !request.text().isBlank())
                .map(request -> new EventState.CriterionInput(request.text().trim(), request.kind(), request.check(), request.value()))
                .toList();
    }

    private static void requireKey(String key) {
        if (EventParamKey.byKey(key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный параметр мероприятия: " + key);
        }
    }

    private static void requireQuestion(String key) {
        if (EventParamKey.byKey(key).map(EventParamKey::question).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет уточняющего вопроса с ключом: " + key);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
