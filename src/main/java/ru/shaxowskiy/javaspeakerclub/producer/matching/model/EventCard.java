package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Карточка мероприятия для подбора спикеров (US-01, US-03, US-07): параметры со статусами, критерии и уточняющие вопросы.
 * params содержит все параметры из п. 5.1 ТЗ — неизвестные тоже (в конце списка), чтобы недостаток данных был виден, а не скрыт.
 * summary — те же параметры одной строкой для человека: значение и откуда оно (указано, выведено ИИ, неизвестно).
 */
public record EventCard(
        String title,
        String status,
        List<String> summary,
        List<Question> questions,
        List<Param> params,
        List<Criterion> criteria,
        List<String> tags,
        List<String> notes,
        List<String> changesSinceLastMatching,
        String description,
        String id,
        LocalDateTime updatedDate
) {

    public static final String STATUS_ANALYZED = "analyzed";
    public static final String STATUS_MATCHED = "matched";

    /** Подтверждён: указан организатором явно (в форме, тексте или ответе на вопрос). */
    public static final String CONFIRMED = "confirmed";
    /** Выведен из контекста: вывод AI по тексту, всегда с цитатой. Не выдаётся за факт. */
    public static final String INFERRED = "inferred";
    /** Неизвестен: данных нет, значение не подставляется. */
    public static final String UNKNOWN = "unknown";

    public record Param(
            String key,
            String label,
            List<String> values,
            String status,
            String source,
            String quote
    ) {
    }

    /**
     * kind: mandatory или desirable. check — что именно проверяется у кандидата
     * (language, city, attendance, competency, format, audience, other).
     * interpretation = true, если обязательность определил AI, а не организатор (п. 5.4 ТЗ).
     */
    public record Criterion(
            String id,
            String text,
            String kind,
            String check,
            String value,
            boolean interpretation,
            String source,
            String quote
    ) {
    }

    /** status: open, answered или skipped. Пропуск не останавливает подбор, но снижает уверенность (US-02). */
    public record Question(
            String key,
            String question,
            List<String> options,
            String status,
            String answer
    ) {
    }

    public Param param(EventParamKey key) {
        return params.stream().filter(param -> param.key().equals(key.key())).findFirst().orElse(null);
    }

    public List<String> values(EventParamKey key) {
        Param param = param(key);
        return param == null || UNKNOWN.equals(param.status()) ? List.of() : param.values();
    }

    public String value(EventParamKey key) {
        List<String> values = values(key);
        return values.isEmpty() ? null : values.get(0);
    }

    public boolean known(EventParamKey key) {
        return !values(key).isEmpty();
    }
}
