package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Параметры мероприятия из п. 5.1 ТЗ. Вопрос задаётся только для параметров, без которых подбор заметно хуже (US-02);
 * порядок констант — приоритет вопросов.
 */
public enum EventParamKey {

    TOPICS("topics", "Тематика и основные темы", true, null,
            "Какие темы должны раскрыть спикеры?"),
    AUDIENCE_LEVEL("audienceLevel", "Уровень аудитории", false, options(
            "junior", "начинающие", "middle", "средний уровень", "senior", "опытные",
            "mixed", "смешанная", "executive", "руководители"),
            "Для какого уровня аудитории требуется спикер?"),
    TALK_TYPE("talkType", "Тип выступления", false, options(
            "practical", "практический кейс", "overview", "обзорный доклад", "workshop", "воркшоп",
            "panel", "панельная дискуссия", "keynote", "ключевой доклад"),
            "Требуется практический кейс или обзорный доклад?"),
    ATTENDANCE("attendance", "Онлайн или офлайн", false, options(
            "offline", "только офлайн", "online", "онлайн", "hybrid", "гибридный формат"),
            "Допустимо ли онлайн-выступление?"),
    SPEAKER_REQUIREMENTS("speakerRequirements", "Требования к спикеру", true, null,
            "Есть ли обязательные требования к опыту эксперта?"),
    LANGUAGE("language", "Язык", false, null, "На каком языке нужно выступление?"),
    CITY("city", "География (город)", false, null, "В каком городе проходит мероприятие?"),
    SUBTOPICS("subtopics", "Подтемы", true, null, null),
    AUDIENCE("audience", "Целевая аудитория и её роли", true, null, null),
    GOAL("goal", "Цель мероприятия", false, null, null),
    INDUSTRY("industry", "Отрасль", false, null, null),
    EVENT_FORMAT("eventFormat", "Формат мероприятия", false, options(
            "conference", "конференция", "meetup", "митап", "webinar", "вебинар", "workshop", "воркшоп",
            "other", "другое"), null),
    TALK_DURATION("talkDuration", "Длительность выступления, минут", false, null, null),
    DATE("date", "Дата мероприятия", false, null, null),
    CONSTRAINTS("constraints", "Ограничения и требования организатора", true, null, null),
    WISHES("wishes", "Дополнительные пожелания", true, null, null);

    private static final Pattern NUMBER = Pattern.compile("(\\d{1,3})");

    private final String key;
    private final String label;
    private final boolean list;
    private final Map<String, String> options;
    private final String question;

    EventParamKey(String key, String label, boolean list, Map<String, String> options, String question) {
        this.key = key;
        this.label = label;
        this.list = list;
        this.options = options;
        this.question = question;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public boolean list() {
        return list;
    }

    /** Допустимые значения и их подписи; null — свободный текст. */
    public Map<String, String> options() {
        return options;
    }

    /** Значения для человека: коды вариантов (offline, senior) заменяются подписями («только офлайн», «опытные»). */
    public String display(List<String> values) {
        return values.stream()
                .map(value -> options == null ? value : options.getOrDefault(value, value))
                .collect(Collectors.joining(", "));
    }

    /** Уточняющий вопрос; null — параметр не критичен для подбора. */
    public String question() {
        return question;
    }

    public static Optional<EventParamKey> byKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }

    /**
     * Приводит значение к единому виду. Пусто — значение не распознано, и параметр остаётся неизвестным:
     * подставлять догадку вместо неизвестного нельзя (п. 5.2 ТЗ).
     */
    public Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return switch (this) {
            case AUDIENCE_LEVEL -> match(lower, List.of(
                    entry("junior", "junior", "джун", "начина", "новичк", "студент"),
                    entry("middle", "middle", "мидл", "средн"),
                    entry("senior", "senior", "сеньор", "синьор", "опытн", "продвинут"),
                    entry("mixed", "mixed", "смешан", "разного", "любого"),
                    entry("executive", "executive", "руковод", "c-level", "топ-менедж", "cto", "ceo")));
            case TALK_TYPE -> match(lower, List.of(
                    entry("practical", "practical", "практи", "кейс", "case"),
                    entry("overview", "overview", "обзор"),
                    entry("workshop", "workshop", "воркшоп", "мастер-класс"),
                    entry("panel", "panel", "панел", "дискусс"),
                    entry("keynote", "keynote", "ключев", "пленар")));
            case EVENT_FORMAT -> match(lower, List.of(
                    entry("conference", "conference", "конференц"),
                    entry("meetup", "meetup", "митап", "встреч"),
                    entry("webinar", "webinar", "вебинар"),
                    entry("workshop", "workshop", "воркшоп", "мастер-класс"),
                    entry("other", "other", "другое")));
            case ATTENDANCE -> match(lower, List.of(
                    entry("hybrid", "hybrid", "гибрид"),
                    entry("offline", "offline", "офлайн", "оффлайн", "очно", "лично", "нет"),
                    entry("online", "online", "онлайн", "дистанц", "удален", "да")));
            case LANGUAGE -> Optional.of(normalizeLanguage(lower));
            case TALK_DURATION -> minutes(lower);
            case DATE -> isoDate(value);
            default -> Optional.of(value);
        };
    }

    /**
     * Синонимы проверяются по порядку: сначала точный код или короткий ответ («да», «нет»), затем вхождение
     * слова длиннее трёх букв — иначе «интернет-конференция» распознавалась бы как «нет», то есть только офлайн.
     */
    private static Optional<String> match(String lower, List<Map.Entry<String, List<String>>> synonyms) {
        for (Map.Entry<String, List<String>> entry : synonyms) {
            if (entry.getValue().contains(lower)) {
                return Optional.of(entry.getKey());
            }
        }
        for (Map.Entry<String, List<String>> entry : synonyms) {
            if (entry.getValue().stream().anyMatch(word -> word.length() > 3 && lower.contains(word))) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static Map.Entry<String, List<String>> entry(String code, String... synonyms) {
        return Map.entry(code, List.of(synonyms));
    }

    static String normalizeLanguage(String lower) {
        if (lower.startsWith("рус") || lower.equals("ru") || lower.startsWith("russian")) {
            return "русский";
        }
        if (lower.startsWith("англ") || lower.equals("en") || lower.startsWith("english")) {
            return "английский";
        }
        return lower;
    }

    private static Optional<String> minutes(String lower) {
        Matcher matcher = NUMBER.matcher(lower);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(matcher.group(1));
        int value = lower.contains("час") || lower.contains("hour") ? number * 60 : number;
        return value >= 5 && value <= 480 ? Optional.of(String.valueOf(value)) : Optional.empty();
    }

    private static Optional<String> isoDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value).toString());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Map<String, String> options(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
