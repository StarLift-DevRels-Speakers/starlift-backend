package ru.shaxowskiy.javaspeakerclub.producer.model;

import java.util.List;

/**
 * Сырой ответ LLM. Ничему из него не доверяем, пока не прошёл ProducerPlanValidator.
 */
public record ProducerPlanDraft(
        String summary,
        List<Theme> strengths,
        List<Theme> growthAreas,
        List<Step> steps
) {

    public record Theme(
            String title,
            List<String> feedbackIds
    ) {
    }

    public record Step(
            String title,
            String why,
            String dueDate,
            List<Evidence> evidence
    ) {
    }

    /**
     * type: FEEDBACK, TALK или CFP; id — идентификатор из снимка.
     */
    public record Evidence(
            String type,
            String id
    ) {
    }
}
