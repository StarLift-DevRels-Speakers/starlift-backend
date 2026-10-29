package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import ru.shaxowskiy.javaspeakerclub.producer.llm.LenientStringDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

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
            @JsonDeserialize(using = LenientStringDeserializer.class) String status,
            @JsonDeserialize(using = LenientStringDeserializer.class) String quote
    ) {
    }

    public record DraftCriterion(
            @JsonDeserialize(using = LenientStringDeserializer.class) String text,
            @JsonDeserialize(using = LenientStringDeserializer.class) String kind,
            @JsonDeserialize(using = LenientStringDeserializer.class) String check,
            @JsonDeserialize(using = LenientStringDeserializer.class) String value,
            @JsonDeserialize(using = LenientStringDeserializer.class) String status,
            @JsonDeserialize(using = LenientStringDeserializer.class) String quote
    ) {
    }
}
