package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Общие части рекомендаций. Пользователю не показываются внутренние коэффициенты (п. 26 ТЗ):
 * только группа, уровень уверенности и объяснение на фактах.
 */
public final class Recommendation {

    public static final String GROUP_HIGH = "high";
    public static final String GROUP_POTENTIAL = "potential";
    public static final String GROUP_NEEDS_CHECK = "needs_check";

    public static final String CONFIDENCE_HIGH = "high";
    public static final String CONFIDENCE_MEDIUM = "medium";
    public static final String CONFIDENCE_LOW = "low";

    private Recommendation() {
    }

    /** Группа по релевантности (п. 11 ТЗ, US-06); comparisons объясняют, чем отличаются близкие варианты. */
    public record Group<T>(String title, List<T> items, List<String> comparisons, String code, String description) {

        public Group<T> withItems(List<T> newItems) {
            return new Group<>(title, newItems, comparisons, code, description);
        }
    }

    /** Уверенность отдельно от релевантности (п. 19 ТЗ): насколько надёжно подтверждены данные для вывода. */
    public record Confidence(String level, List<String> reasons) {
    }

    /** Доклад-подтверждение с темами мероприятия, которые он закрывает. */
    public record TalkRef(String id, String topic, String event, LocalDate date, String format, List<String> matchedTopics) {
    }

    public static String groupTitle(String code) {
        return switch (code) {
            case GROUP_HIGH -> "Высокая релевантность";
            case GROUP_POTENTIAL -> "Потенциально релевантные";
            default -> "Требуют дополнительной проверки";
        };
    }

    public static String groupDescription(String code) {
        return switch (code) {
            case GROUP_HIGH -> "Хорошее соответствие ключевым требованиям и достаточно подтверждающих данных.";
            case GROUP_POTENTIAL -> "Основное соответствие есть, но есть ограничения или не хватает информации.";
            default -> "Есть основания рассмотреть, но данных недостаточно для уверенной рекомендации.";
        };
    }

    public static String confidenceLevel(double score) {
        if (score >= 0.75) {
            return CONFIDENCE_HIGH;
        }
        return score >= 0.5 ? CONFIDENCE_MEDIUM : CONFIDENCE_LOW;
    }
}
