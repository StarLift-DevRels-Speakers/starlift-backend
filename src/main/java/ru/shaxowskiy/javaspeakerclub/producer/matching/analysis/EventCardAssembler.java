package ru.shaxowskiy.javaspeakerclub.producer.matching.analysis;

import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft.DraftCriterion;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard.Criterion;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard.Param;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard.Question;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventState;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Собирает карточку мероприятия из сохранённого состояния (US-01 — US-03, US-07). Приоритет источников:
 * поля организатора → ответы на уточняющие вопросы → разбор описания. Из ответа LLM берётся только то,
 * что подтверждено дословной цитатой из описания; значение без цитаты отбрасывается, а не подставляется (п. 5.2 ТЗ).
 */
public final class EventCardAssembler {

    public static final String SOURCE_ORGANIZER = "organizer";
    public static final String SOURCE_ANSWER = "answer";
    public static final String SOURCE_DESCRIPTION = "description";

    public static final String MANDATORY = "mandatory";
    public static final String DESIRABLE = "desirable";

    public static final String QUESTION_OPEN = "open";
    public static final String QUESTION_ANSWERED = "answered";
    public static final String QUESTION_SKIPPED = "skipped";

    public static final List<String> CHECKS = List.of("language", "city", "attendance", "competency", "industry", "topic",
            "format", "audience", "other");

    /** Явные слова обязательности: если их нет в цитате, обязательность — интерпретация AI (п. 5.4 ТЗ). */
    private static final List<String> EXPLICIT_MANDATORY = List.of("обязател", "необходим", "требует", "должен", "должна",
            "должны", "только", "строго", "must", "required", "only");
    private static final Set<String> NO_REQUIREMENTS = Set.of("нет", "no", "не важно", "неважно", "без требований",
            "любой", "любые", "нет обязательных требований");
    private static final String[] MONTHS = {"январ", "феврал", "март", "апрел", "мая", "июн", "июл", "август", "сентябр",
            "октябр", "ноябр", "декабр"};

    private EventCardAssembler() {
    }

    public static EventCard assemble(UUID id, String title, String description, String status, EventState state,
                                     List<String> vocabulary, int maxQuestions, LocalDateTime updatedDate) {
        List<String> notes = new ArrayList<>(state.analysisNotes() == null ? List.of() : state.analysisNotes());
        Map<String, Param> params = new LinkedHashMap<>();
        fromDraft(state.draft(), description, params, notes);
        state.answers().forEach((key, values) -> put(params, key, values, SOURCE_ANSWER, notes));
        state.structured().forEach((key, values) -> put(params, key, values, SOURCE_ORGANIZER, notes));

        List<Param> ordered = new ArrayList<>();
        List<Param> unknown = new ArrayList<>();
        for (EventParamKey key : EventParamKey.values()) {
            Param param = params.get(key.key());
            if (param == null) {
                unknown.add(unknown(key));
            } else {
                ordered.add(param);
            }
        }
        ordered.addAll(unknown);

        List<Criterion> criteria = criteria(state, description, notes);
        List<String> tags = tags(state, vocabulary, title, description, notes);
        List<Question> questions = questions(ordered, criteria, state, maxQuestions, notes);
        EventCard card = new EventCard(title, status, summary(ordered), questions, ordered, criteria, tags, notes, List.of(),
                description, id.toString(), updatedDate);
        return new EventCard(card.title(), card.status(), card.summary(), card.questions(), card.params(), card.criteria(),
                card.tags(), card.notes(), changes(card, state), card.description(), card.id(), card.updatedDate());
    }

    /** Параметры для человека: значение и откуда оно. Неизвестные — только те, о которых задаётся вопрос. */
    static List<String> summary(List<Param> params) {
        List<String> lines = new ArrayList<>();
        for (Param param : params) {
            Optional<EventParamKey> key = EventParamKey.byKey(param.key());
            boolean critical = key.map(item -> item.question() != null).orElse(false);
            if (EventCard.UNKNOWN.equals(param.status()) && !critical) {
                continue;
            }
            String status = switch (param.status()) {
                case EventCard.CONFIRMED -> "подтверждено";
                case EventCard.INFERRED -> "вывод ИИ по описанию";
                default -> "неизвестно";
            };
            String values = param.values().isEmpty() ? "—"
                    : key.map(item -> item.display(param.values())).orElse(String.join(", ", param.values()));
            lines.add(param.label() + ": " + values + " (" + status + ")");
        }
        return lines;
    }

    /** Известные параметры — снимок требований для сравнения при следующем подборе (US-07). */
    public static Map<String, List<String>> snapshot(EventCard card) {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        card.params().stream()
                .filter(param -> !EventCard.UNKNOWN.equals(param.status()))
                .forEach(param -> snapshot.put(param.key(), param.values()));
        if (!card.tags().isEmpty()) {
            snapshot.put("tags", card.tags());
        }
        return snapshot;
    }

    public static List<String> criteriaSnapshot(EventCard card) {
        return card.criteria().stream()
                .map(criterion -> (MANDATORY.equals(criterion.kind()) ? "обязательно" : "желательно") + ": " + criterion.text())
                .toList();
    }

    public static String criterionId(String text) {
        return UUID.nameUUIDFromBytes(flatten(text).getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
    }

    public static boolean noRequirements(String value) {
        return value != null && NO_REQUIREMENTS.contains(flatten(value));
    }

    /** Ответы на вопросы и поля формы: для списочных параметров «a, b; c» — три значения. */
    public static List<String> splitValues(EventParamKey key, List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> key.list() ? Arrays.stream(value.split("[,;\\n]")) : Stream.of(value))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static void fromDraft(EventAnalysisDraft draft, String description, Map<String, Param> params, List<String> notes) {
        if (draft == null || draft.params() == null || description == null || description.isBlank()) {
            return;
        }
        List<String> dropped = new ArrayList<>();
        draft.params().forEach((rawKey, draftParam) -> {
            EventParamKey key = EventParamKey.byKey(rawKey).orElse(null);
            if (key == null || draftParam == null || draftParam.value() == null || draftParam.value().isEmpty()) {
                return;
            }
            List<String> values = normalize(key, draftParam.value());
            if (values.isEmpty()) {
                return;
            }
            String quote = draftParam.quote() == null ? "" : draftParam.quote().trim();
            if (!quoteFound(description, quote)) {
                dropped.add(key.label().toLowerCase(Locale.ROOT) + " («" + String.join(", ", values) + "»)");
                return;
            }
            boolean stated = "stated".equalsIgnoreCase(draftParam.status()) && saidInQuote(key, values, quote);
            params.put(key.key(), new Param(key.key(), key.label(), values,
                    stated ? EventCard.CONFIRMED : EventCard.INFERRED, SOURCE_DESCRIPTION, quote));
        });
        if (!dropped.isEmpty()) {
            notes.add("Отброшены значения, которых нет в описании: " + String.join("; ", dropped) + ".");
        }
    }

    /** «Подтверждён» — только если значение действительно сказано в цитате; иначе это вывод AI по контексту. */
    static boolean saidInQuote(EventParamKey key, List<String> values, String quote) {
        if (key.options() != null || key == EventParamKey.TALK_DURATION) {
            return key.normalize(quote).map(values::contains).orElse(false);
        }
        if (key == EventParamKey.DATE) {
            return values.stream().allMatch(value -> dateInQuote(value, quote));
        }
        Set<String> quoteKeys = TermMatcher.keys(quote);
        return values.stream().allMatch(value -> {
            // Для одного слова keys() добавляет и слово целиком: «Москва» не нашлась бы в «в Москве». Сравниваем по основам.
            Set<String> keys = new LinkedHashSet<>(TermMatcher.keys(value));
            if (keys.size() > 1) {
                keys.remove(TermMatcher.normalize(value));
            }
            return !keys.isEmpty() && quoteKeys.containsAll(keys) || flatten(quote).contains(flatten(value));
        });
    }

    private static boolean dateInQuote(String isoDate, String quote) {
        try {
            LocalDate date = LocalDate.parse(isoDate);
            String text = flatten(quote);
            boolean day = text.matches(".*(^|\\D)0?" + date.getDayOfMonth() + "(\\D|$).*");
            boolean month = text.contains(MONTHS[date.getMonthValue() - 1])
                    || text.matches(".*(^|\\D)0?" + date.getDayOfMonth() + "\\.0?" + date.getMonthValue() + "(\\D|$).*");
            return day && month || text.contains(isoDate);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static void put(Map<String, Param> params, String rawKey, List<String> rawValues, String source, List<String> notes) {
        EventParamKey key = EventParamKey.byKey(rawKey).orElse(null);
        if (key == null) {
            return;
        }
        List<String> values = normalize(key, splitValues(key, rawValues));
        if (key == EventParamKey.SPEAKER_REQUIREMENTS && values.stream().anyMatch(EventCardAssembler::noRequirements)) {
            values = List.of("нет обязательных требований");
        }
        if (values.isEmpty()) {
            return;
        }
        Param previous = params.get(key.key());
        if (previous != null && SOURCE_DESCRIPTION.equals(previous.source()) && !previous.values().equals(values)) {
            notes.add(key.label() + ": в описании — «" + String.join(", ", previous.values()) + "», указано организатором — «"
                    + String.join(", ", values) + "». Используется значение организатора.");
        }
        params.put(key.key(), new Param(key.key(), key.label(), values, EventCard.CONFIRMED, source, null));
    }

    private static List<String> normalize(EventParamKey key, List<String> raw) {
        return raw.stream()
                .map(key::normalize)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private static Param unknown(EventParamKey key) {
        return new Param(key.key(), key.label(), List.of(), EventCard.UNKNOWN, null, null);
    }

    private static List<Criterion> criteria(EventState state, String description, List<String> notes) {
        Map<String, Criterion> criteria = new LinkedHashMap<>();
        for (EventState.CriterionInput input : state.organizerCriteria()) {
            if (input == null || input.text() == null || input.text().isBlank()) {
                continue;
            }
            String id = criterionId(input.text());
            String check = check(input.check());
            criteria.put(id, new Criterion(id, input.text().trim(), kind(input.kind()), check, value(check, input.value()),
                    false, SOURCE_ORGANIZER, null));
        }
        List<String> requirements = splitValues(EventParamKey.SPEAKER_REQUIREMENTS,
                state.answers().getOrDefault(EventParamKey.SPEAKER_REQUIREMENTS.key(), List.of()));
        for (String answer : requirements) {
            if (!noRequirements(answer)) {
                String id = criterionId(answer);
                criteria.putIfAbsent(id, new Criterion(id, answer, MANDATORY, "competency", answer, false, SOURCE_ANSWER, null));
            }
        }
        EventAnalysisDraft draft = state.draft();
        if (draft == null || draft.criteria() == null || description == null) {
            return List.copyOf(criteria.values());
        }
        List<String> dropped = new ArrayList<>();
        for (DraftCriterion item : draft.criteria()) {
            if (item == null || item.text() == null || item.text().isBlank()) {
                continue;
            }
            String id = criterionId(item.text());
            if (criteria.containsKey(id) || state.rejectedCriteria().contains(id)) {
                continue;
            }
            String quote = item.quote() == null ? "" : item.quote().trim();
            if (!quoteFound(description, quote)) {
                dropped.add("«" + item.text().trim() + "»");
                continue;
            }
            String kind = kind(item.kind());
            boolean explicit = EXPLICIT_MANDATORY.stream().anyMatch(marker -> flatten(quote).contains(marker));
            boolean interpretation = MANDATORY.equals(kind) && (!explicit || "inferred".equalsIgnoreCase(item.status()));
            String check = check(item.check());
            criteria.put(id, new Criterion(id, item.text().trim(), kind, check, value(check, item.value()),
                    interpretation, SOURCE_DESCRIPTION, quote));
        }
        if (!dropped.isEmpty()) {
            notes.add("Отброшены требования, которых нет в описании: " + String.join(", ", dropped) + ".");
        }
        return List.copyOf(criteria.values());
    }

    /**
     * Теги организатора берутся как есть. Тег от модели остаётся, только если он есть в словаре платформы
     * и его тема встречается в названии или описании: иначе это догадка, которая расширила бы подбор.
     */
    private static List<String> tags(EventState state, List<String> vocabulary, String title, String description,
                                     List<String> notes) {
        Set<String> known = new LinkedHashSet<>(vocabulary.stream().map(TermMatcher::normalizeTag).toList());
        Set<String> tags = new LinkedHashSet<>();
        state.structured().getOrDefault("tags", List.of()).stream()
                .filter(Objects::nonNull)
                .filter(tag -> !tag.isBlank())
                .forEach(tag -> tags.add(TermMatcher.normalizeTag(tag)));
        if (state.draft() != null && state.draft().tags() != null) {
            Set<String> textKeys = TermMatcher.keys((title == null ? "" : title) + " " + (description == null ? "" : description));
            List<String> dropped = new ArrayList<>();
            state.draft().tags().stream()
                    .filter(Objects::nonNull)
                    .map(TermMatcher::normalizeTag)
                    .filter(known::contains)
                    .filter(tag -> !tags.contains(tag))
                    .forEach(tag -> {
                        if (TermMatcher.matches(TermMatcher.term(tag), textKeys)) {
                            tags.add(tag);
                        } else {
                            dropped.add(tag);
                        }
                    });
            if (!dropped.isEmpty()) {
                notes.add("Отброшены теги, которых нет в описании: " + String.join(", ", dropped) + ".");
            }
        }
        return List.copyOf(tags);
    }

    /**
     * Уточняющие вопросы только по неизвестным критичным параметрам (US-02), по приоритету и не больше maxQuestions.
     * Ответ, из которого не удалось извлечь значение, считается пропуском: параметр остаётся неизвестным.
     */
    private static List<Question> questions(List<Param> params, List<Criterion> criteria, EventState state,
                                            int maxQuestions, List<String> notes) {
        Map<String, Param> byKey = new LinkedHashMap<>();
        params.forEach(param -> byKey.put(param.key(), param));
        List<Question> questions = new ArrayList<>();
        int pending = 0;
        for (EventParamKey key : EventParamKey.values()) {
            if (key.question() == null) {
                continue;
            }
            List<String> options = key.options() == null ? List.of() : List.copyOf(key.options().values());
            if (state.answers().containsKey(key.key())) {
                String answer = String.join(", ", state.answers().get(key.key()));
                boolean recognized = !EventCard.UNKNOWN.equals(byKey.get(key.key()).status());
                if (!recognized) {
                    notes.add("Ответ «" + answer + "» на вопрос «" + key.question() + "» не распознан — параметр остался неизвестным.");
                }
                questions.add(new Question(key.key(), key.question(), options,
                        recognized ? QUESTION_ANSWERED : QUESTION_SKIPPED, answer));
                continue;
            }
            if (!needsQuestion(key, byKey, criteria) || pending >= maxQuestions) {
                continue;
            }
            pending++;
            questions.add(new Question(key.key(), key.question(), options,
                    state.skipped().contains(key.key()) ? QUESTION_SKIPPED : QUESTION_OPEN, null));
        }
        return questions;
    }

    private static boolean needsQuestion(EventParamKey key, Map<String, Param> params, List<Criterion> criteria) {
        if (!EventCard.UNKNOWN.equals(params.get(key.key()).status())) {
            return false;
        }
        return switch (key) {
            case TOPICS -> EventCard.UNKNOWN.equals(params.get(EventParamKey.SUBTOPICS.key()).status());
            case SPEAKER_REQUIREMENTS -> criteria.stream().noneMatch(criterion -> MANDATORY.equals(criterion.kind()));
            case CITY -> {
                Param attendance = params.get(EventParamKey.ATTENDANCE.key());
                yield !EventCard.UNKNOWN.equals(attendance.status()) && !attendance.values().contains("online");
            }
            default -> true;
        };
    }

    private static List<String> changes(EventCard card, EventState state) {
        if (state.matchedParams() == null) {
            return List.of();
        }
        List<String> changes = new ArrayList<>();
        Map<String, List<String>> now = snapshot(card);
        List<String> keys = new ArrayList<>(Arrays.stream(EventParamKey.values()).map(EventParamKey::key).toList());
        keys.add("tags");
        for (String key : keys) {
            List<String> before = state.matchedParams().getOrDefault(key, List.of());
            List<String> after = now.getOrDefault(key, List.of());
            if (!before.equals(after)) {
                String label = EventParamKey.byKey(key).map(EventParamKey::label).orElse("Теги");
                changes.add(label + ": " + (before.isEmpty() ? "не указано" : String.join(", ", before))
                        + " → " + (after.isEmpty() ? "не указано" : String.join(", ", after)));
            }
        }
        List<String> criteriaNow = criteriaSnapshot(card);
        List<String> before = state.matchedCriteria() == null ? List.of() : state.matchedCriteria();
        criteriaNow.stream().filter(item -> !before.contains(item)).forEach(item -> changes.add("добавлено требование «" + item + "»"));
        before.stream().filter(item -> !criteriaNow.contains(item)).forEach(item -> changes.add("убрано требование «" + item + "»"));
        return changes;
    }

    private static String kind(String kind) {
        return MANDATORY.equalsIgnoreCase(kind) || "обязательное".equalsIgnoreCase(kind) ? MANDATORY : DESIRABLE;
    }

    private static String check(String check) {
        String value = check == null ? "other" : check.trim().toLowerCase(Locale.ROOT);
        return CHECKS.contains(value) ? value : "other";
    }

    /** Язык и формат участия приводятся к тем же значениям, что и параметры карточки, иначе их не сравнить. */
    private static String value(String check, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (check) {
            case "language" -> EventParamKey.LANGUAGE.normalize(raw).orElse(raw.trim());
            case "attendance" -> EventParamKey.ATTENDANCE.normalize(raw).orElse(null);
            default -> raw.trim();
        };
    }

    /** Цитата должна дословно встречаться в описании; пропуск внутри цитаты («…») допускается. */
    static boolean quoteFound(String description, String quote) {
        if (description == null || quote == null || quote.isBlank()) {
            return false;
        }
        String text = flatten(description);
        List<String> parts = Arrays.stream(quote.split("\\.\\.\\.|…"))
                .map(EventCardAssembler::flatten)
                .filter(part -> part.length() >= 3)
                .toList();
        return !parts.isEmpty() && parts.stream().allMatch(text::contains);
    }

    /** Для сравнения текста: регистр, «ё», кавычки, тире, лишние пробелы и знаки по краям не важны. */
    static String flatten(String text) {
        return TermMatcher.normalize(text)
                .replaceAll("[«»\"“”„]", "")
                .replaceAll("[—–]", "-")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
                .trim();
    }
}
