package ru.shaxowskiy.javaspeakerclub.producer.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки подбора по ТЗ AI-продюсера: мероприятие → спикеры и эксперт → мероприятия/CFP.
 * Версия алгоритма и версия промпта пишутся в журнал каждого запуска.
 * aiSummaries — короткие пояснения модели к первым aiSummaryLimit рекомендациям; объяснение кода есть всегда.
 * По умолчанию выключены: небольшие локальные модели путают факты разных кандидатов, а проверка ловит не всё.
 * Включать с моделью уровня gpt-4o-mini: app.producer.matching.ai-summaries=true.
 */
@ConfigurationProperties(prefix = "app.producer.matching")
public record MatchingProperties(
        @DefaultValue("event-analysis-v1") String analysisPromptVersion,
        @DefaultValue("recommendation-summary-v1") String summaryPromptVersion,
        @DefaultValue("matching-v1") String algorithmVersion,
        @DefaultValue("4") int maxQuestions,
        @DefaultValue("10") int maxCandidates,
        @DefaultValue("8") int maxEvents,
        @DefaultValue("300") int vocabularyLimit,
        @DefaultValue("false") boolean aiSummaries,
        @DefaultValue("3") int aiSummaryLimit
) {
}
