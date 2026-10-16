package ru.shaxowskiy.javaspeakerclub.producer.matching.api;

import java.util.List;

/**
 * Метрики качества AI-продюсера (п. 28 ТЗ): воронки по фактическим действиям и доли из журнала запусков.
 * conversion — процент от предыдущего шага, null для первого шага или если предыдущий шаг пуст.
 */
public record MetricsView(
        List<Step> organizerFunnel,
        List<Step> expertFunnel,
        List<Share> quality
) {

    public record Step(String step, int count, Integer conversion) {
    }

    public record Share(String metric, int part, int total, Integer percent) {
    }
}
