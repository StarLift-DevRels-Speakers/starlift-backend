package ru.shaxowskiy.javaspeakerclub.audience.profile;

import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.AcceptedTalk;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.EventInfo;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.PastEdition;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfile.Evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Пропускает в профиль только то, что опирается на входные данные: ссылки на источники проверяются,
 * доли уровней приводятся к 100%, а уверенность не может быть выше, чем позволяют данные.
 */
@Component
public class EventAudienceProfileValidator {

    public static final List<String> LEVELS = List.of("junior", "middle", "senior", "lead");
    public static final List<String> SEGMENTS = List.of("schoolchildren", "students", "beginners", "practitioners", "experts");
    public static final List<String> CONFIDENCE = List.of("low", "medium", "high");
    public static final List<String> VISUAL = List.of("low", "medium", "high");

    private static final Map<String, String> SEGMENT_LABELS = Map.of(
            "schoolchildren", "Школьники",
            "students", "Студенты",
            "beginners", "Начинающие специалисты",
            "practitioners", "Практикующие специалисты",
            "experts", "Опытные эксперты"
    );
    private static final Map<String, String> LEVEL_ALIASES = Map.of("teamlead", "lead", "team lead", "lead", "lead+", "lead");
    private static final Map<String, String> SEGMENT_ALIASES = Map.of(
            "school", "schoolchildren", "pupils", "schoolchildren",
            "student", "students",
            "beginner", "beginners", "juniors", "beginners",
            "practitioner", "practitioners", "practice", "practitioners",
            "expert", "experts"
    );
    // Возраст принимается, только если в поле мероприятия он назван прямо или через того, кто им обладает.
    private static final Pattern AGE_MARKER = Pattern.compile(
            "\\d{1,2}\\s*\\+|\\d{1,2}\\s*[-–—]\\s*\\d{1,2}|лет|школ|студент|вуз|курс|абитуриент|колледж|университет|подрост",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<String> AGE_FIELDS = Set.of("name", "topic", "audience", "description");
    private static final int MIN_AGE = 6;
    private static final int MAX_AGE = 99;
    private static final int MAX_LABEL_LENGTH = 80;
    private static final int MAX_REASON_LENGTH = 300;

    private static final Map<String, String> EVENT_FIELDS = Map.of(
            "name", "Название мероприятия",
            "topic", "Тема мероприятия",
            "audience", "Заявленная аудитория",
            "description", "Описание мероприятия",
            "format", "Формат мероприятия"
    );
    private static final int MAX_ITEMS = 6;
    private static final int MAX_ITEM_LENGTH = 120;
    private static final int MAX_SUMMARY_LENGTH = 600;
    private static final int MAX_EVIDENCE = 12;
    private static final int RICH_DESCRIPTION_CHARS = 150;

    public record Result(EventAudienceProfile profile, List<Rejection> rejections) {
    }

    public Result validate(EventAudienceProfileDraft draft, EventAudienceInputs inputs) {
        List<Rejection> rejections = new ArrayList<>();
        Map<String, Integer> levels = normalizeLevels(draft.levels(), rejections);
        Map<String, Integer> experience = normalizeShares(draft.experience(), SEGMENTS, SEGMENT_ALIASES, "Сегмент", rejections);
        String modelSegment = segmentKey(draft.primarySegment());
        if (experience.isEmpty() && modelSegment != null) {
            experience = single(modelSegment);
        }
        if (experience.isEmpty() && !levels.isEmpty()) {
            experience = experienceFromLevels(levels);
            rejections.add(new Rejection("Преимущественная ЦА", "Модель не дала доли сегментов — выведены из уровней"));
        }
        String segment = primary(experience, modelSegment);
        if (modelSegment != null && segment != null && !modelSegment.equals(segment)) {
            rejections.add(new Rejection("Преимущественная ЦА", "Сегмент модели «" + modelSegment + "» заменён на «" + segment
                    + "» — у него наибольшая доля"));
        }
        List<Evidence> evidence = validateEvidence(draft.evidence(), inputs, rejections);

        String cap = confidenceCap(inputs);
        String confidence = normalizeConfidence(draft.confidence());
        if (confidence == null) {
            confidence = cap;
        } else if (CONFIDENCE.indexOf(confidence) > CONFIDENCE.indexOf(cap)) {
            rejections.add(new Rejection("Уверенность", "Снижена с " + confidence + " до " + cap + ": данных о мероприятии мало"));
            confidence = cap;
        }
        if (segment == null && !"low".equals(confidence)) {
            rejections.add(new Rejection("Уверенность", "Снижена до low: модель не определила преимущественную ЦА"));
            confidence = "low";
        }

        EventAudienceProfile profile = new EventAudienceProfile(
                summary(draft.summary()),
                segment,
                segmentLabel(draft.segmentLabel(), segment, modelSegment),
                Texts.truncate(Texts.trimToNull(draft.segmentReason()), MAX_REASON_LENGTH),
                experience,
                age(draft.age(), inputs.event(), rejections),
                visual(draft.visualExpectation(), segment),
                levels,
                Texts.cleanList(draft.roles(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.domains(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.expectations(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.assumedKnowledge(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.turnoffs(), MAX_ITEMS, MAX_ITEM_LENGTH),
                confidence,
                evidence
        );
        return new Result(profile, List.copyOf(rejections));
    }

    /**
     * Профиль, который ввёл организатор: те же ограничения, источник — сам организатор.
     */
    public EventAudienceProfile manual(EventAudienceProfileUpdate update) {
        List<Rejection> ignored = new ArrayList<>();
        Map<String, Integer> levels = normalizeLevels(update.levels(), ignored);
        String chosen = segmentKey(update.primarySegment());
        Map<String, Integer> experience = chosen != null ? single(chosen) : levels.isEmpty() ? Map.of() : experienceFromLevels(levels);
        String segment = primary(experience, chosen);
        return new EventAudienceProfile(
                summary(update.summary()),
                segment,
                segmentLabel(update.segmentLabel(), segment, chosen),
                "Указано организатором",
                experience,
                ageRange(update.ageFrom(), update.ageTo()),
                visual(update.visualExpectation(), segment),
                levels,
                Texts.cleanList(update.roles(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.domains(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.expectations(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.assumedKnowledge(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.turnoffs(), MAX_ITEMS, MAX_ITEM_LENGTH),
                "high",
                List.of(new Evidence("DEVREL", null, "Уточнено организатором"))
        );
    }

    /**
     * Преимущественная ЦА — сегмент с наибольшей долей; при равенстве — тот, что назвала модель, иначе младший.
     */
    static String primary(Map<String, Integer> experience, String preferred) {
        if (experience.isEmpty()) {
            return null;
        }
        int max = experience.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (preferred != null && experience.getOrDefault(preferred, 0) == max) {
            return preferred;
        }
        return SEGMENTS.stream().filter(segment -> experience.getOrDefault(segment, 0) == max).findFirst().orElse(null);
    }

    /**
     * Когда модель не дала сегменты: junior — начинающие, middle и senior — практики, lead — эксперты.
     */
    static Map<String, Integer> experienceFromLevels(Map<String, Integer> levels) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("beginners", levels.getOrDefault("junior", 0));
        raw.put("practitioners", levels.getOrDefault("middle", 0) + levels.getOrDefault("senior", 0));
        raw.put("experts", levels.getOrDefault("lead", 0));
        return normalizeShares(raw, SEGMENTS, Map.of(), "Сегмент", new ArrayList<>());
    }

    /**
     * Возраст из ответа модели проходит, только если модель указала поле мероприятия, в котором возраст
     * или его носитель («14+», «школьники», «студенты») назван прямо.
     */
    static EventAudienceProfile.AgeRange age(EventAudienceProfileDraft.Age age, EventInfo event, List<Rejection> rejections) {
        if (age == null || (age.from() == null && age.to() == null)) {
            return null;
        }
        String field = age.sourceField() == null ? "" : age.sourceField().trim().toLowerCase(Locale.ROOT);
        String text = AGE_FIELDS.contains(field) ? eventField(event, field) : null;
        if (text == null || !AGE_MARKER.matcher(text).find()) {
            rejections.add(new Rejection("Возраст", "В данных мероприятия возраст прямо не указан — не используем"));
            return null;
        }
        Double from = Texts.number(age.from());
        Double to = Texts.number(age.to());
        EventAudienceProfile.AgeRange range = ageRange(from == null ? null : (int) Math.round(from), to == null ? null : (int) Math.round(to));
        if (range == null) {
            rejections.add(new Rejection("Возраст", "Недопустимый диапазон: " + age.from() + "–" + age.to()));
        }
        return range;
    }

    static EventAudienceProfile.AgeRange ageRange(Integer from, Integer to) {
        if (from == null && to == null) {
            return null;
        }
        if ((from != null && (from < MIN_AGE || from > MAX_AGE)) || (to != null && (to < MIN_AGE || to > MAX_AGE))) {
            return null;
        }
        if (from != null && to != null && from > to) {
            return new EventAudienceProfile.AgeRange(to, from);
        }
        return new EventAudienceProfile.AgeRange(from, to);
    }

    /**
     * Наглядность важнее всего школьникам, студентам и начинающим; экспертам допустимы плотные слайды с кодом.
     */
    static String visual(String raw, String segment) {
        String value = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        if (value != null && VISUAL.contains(value)) {
            return value;
        }
        if (segment == null) {
            return "medium";
        }
        return switch (segment) {
            case "schoolchildren", "students", "beginners" -> "high";
            case "experts" -> "low";
            default -> "medium";
        };
    }

    private static String segmentLabel(String raw, String segment, String modelSegment) {
        String label = Texts.trimToNull(raw);
        // Подпись модели относится к её сегменту; если сегмент заменён, берём стандартную.
        if (label != null && segment != null && segment.equals(modelSegment)) {
            return Texts.truncate(label, MAX_LABEL_LENGTH);
        }
        return segment == null ? null : SEGMENT_LABELS.get(segment);
    }

    static String segmentKey(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        key = SEGMENT_ALIASES.getOrDefault(key, key);
        return SEGMENTS.contains(key) ? key : null;
    }

    private static Map<String, Integer> single(String segment) {
        Map<String, Integer> shares = new LinkedHashMap<>();
        for (String key : SEGMENTS) {
            shares.put(key, key.equals(segment) ? 100 : 0);
        }
        return shares;
    }

    /**
     * Сколько уверенности дают данные: заявленная аудитория и программа из нескольких докладов — high,
     * хоть что-то сверх названия и темы — medium, иначе low.
     */
    static String confidenceCap(EventAudienceInputs inputs) {
        EventInfo event = inputs.event();
        boolean hasAudience = Texts.trimToNull(event.audience()) != null;
        int accepted = inputs.acceptedTalks() == null ? 0 : inputs.acceptedTalks().size();
        boolean richDescription = event.description() != null && event.description().trim().length() >= RICH_DESCRIPTION_CHARS;
        if (hasAudience && accepted >= 2) {
            return "high";
        }
        if (hasAudience || accepted >= 1 || richDescription) {
            return "medium";
        }
        return "low";
    }

    /**
     * Доли уровней в процентах с суммой ровно 100. Неизвестные уровни отбрасываются.
     */
    static Map<String, Integer> normalizeLevels(Map<String, Object> raw, List<Rejection> rejections) {
        return normalizeShares(raw, LEVELS, LEVEL_ALIASES, "Уровень", rejections);
    }

    /**
     * Доли в процентах по ключам keys с суммой ровно 100 (метод наибольшего остатка); пусто, если долей нет.
     */
    static Map<String, Integer> normalizeShares(Map<String, Object> raw, List<String> keys, Map<String, String> aliases,
                                                String itemName, List<Rejection> rejections) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                key = aliases.getOrDefault(key, key);
                Double value = Texts.number(entry.getValue());
                if (!keys.contains(key)) {
                    rejections.add(new Rejection(itemName + " «" + entry.getKey() + "»", "Неизвестное значение"));
                    continue;
                }
                if (value == null || value < 0) {
                    continue;
                }
                values.merge(key, value, Double::sum);
            }
        }
        double sum = values.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            return Map.of();
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Double> remainders = new LinkedHashMap<>();
        int assigned = 0;
        for (String key : keys) {
            double share = values.getOrDefault(key, 0.0) * 100 / sum;
            int floor = (int) Math.floor(share);
            result.put(key, floor);
            remainders.put(key, share - floor);
            assigned += floor;
        }
        List<String> byRemainder = remainders.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (int i = 0; i < 100 - assigned; i++) {
            String key = byRemainder.get(i % byRemainder.size());
            result.put(key, result.get(key) + 1);
        }
        return result;
    }

    private static List<Evidence> validateEvidence(
            List<EventAudienceProfileDraft.Evidence> drafts,
            EventAudienceInputs inputs,
            List<Rejection> rejections
    ) {
        Map<String, AcceptedTalk> talks = index(inputs.acceptedTalks(), AcceptedTalk::id);
        Map<String, PastEdition> editions = index(inputs.pastEditions(), PastEdition::id);
        List<Evidence> accepted = new ArrayList<>();
        for (EventAudienceProfileDraft.Evidence draft : drafts == null ? List.<EventAudienceProfileDraft.Evidence>of() : drafts) {
            if (draft == null) {
                continue;
            }
            String source = draft.source() == null ? "" : draft.source().trim().toUpperCase(Locale.ROOT);
            String id = Texts.trimToNull(draft.id());
            Evidence resolved = switch (source) {
                case "EVENT" -> {
                    String field = id == null ? null : id.toLowerCase(Locale.ROOT);
                    yield field != null && EVENT_FIELDS.containsKey(field) && Texts.trimToNull(eventField(inputs.event(), field)) != null
                            ? new Evidence(source, field, EVENT_FIELDS.get(field))
                            : null;
                }
                case "ACCEPTED_TALK" -> id != null && talks.containsKey(id)
                        ? new Evidence(source, id, "Доклад программы «" + talks.get(id).name() + "»")
                        : null;
                case "PAST_EDITION" -> id != null && editions.containsKey(id)
                        ? new Evidence(source, id, pastEditionLabel(editions.get(id)))
                        : null;
                default -> null;
            };
            if (resolved == null) {
                rejections.add(new Rejection("Источник " + source + " " + id, "Нет во входных данных"));
                continue;
            }
            if (accepted.contains(resolved)) {
                continue;
            }
            if (accepted.size() >= MAX_EVIDENCE) {
                break;
            }
            accepted.add(resolved);
        }
        return List.copyOf(accepted);
    }

    private static String eventField(EventInfo event, String field) {
        return switch (field) {
            case "name" -> event.name();
            case "topic" -> event.topic();
            case "audience" -> event.audience();
            case "description" -> event.description();
            case "format" -> event.format();
            default -> null;
        };
    }

    private static String pastEditionLabel(PastEdition edition) {
        String company = edition.company() == null ? "" : " (" + edition.company() + ")";
        return "Прошлый выпуск «" + edition.conference() + "»: " + edition.speakerName() + company;
    }

    private static String normalizeConfidence(String raw) {
        String value = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        return value != null && CONFIDENCE.contains(value) ? value : null;
    }

    private static String summary(String raw) {
        String summary = Texts.trimToNull(raw);
        return summary == null ? null : Texts.truncate(summary, MAX_SUMMARY_LENGTH);
    }

    private static <T> Map<String, T> index(List<T> items, Function<T, String> id) {
        if (items == null) {
            return Map.of();
        }
        return items.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(id, Function.identity(), (left, right) -> left));
    }
}
