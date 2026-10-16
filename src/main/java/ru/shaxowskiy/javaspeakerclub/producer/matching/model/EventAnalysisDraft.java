package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.util.List;
import java.util.Map;

/**
 * Сырой ответ LLM на разбор описания мероприятия. Ничему не доверяем, пока не прошёл EventCardAssembler:
 * значение без цитаты из описания отбрасывается.
 */
public record EventAnalysisDraft(
        Map<String, DraftParam> params,
        List<String> tags,
        List<DraftCriterion> criteria
) {

    /** status: stated — сказано в тексте прямо, inferred — вывод по контексту. */
    public record DraftParam(
            List<String> value,
            String status,
            String quote
    ) {
    }

    public record DraftCriterion(
            String text,
            String kind,
            String check,
            String value,
            String status,
            String quote
    ) {
    }
}
