package ru.shaxowskiy.javaspeakerclub.producer.model;

import java.time.LocalDate;
import java.util.List;

/**
 * План после проверки: числа, цитаты и подписи доказательств собраны кодом из снимка.
 */
public record ProducerPlan(
        String summary,
        Stage stage,
        List<Theme> strengths,
        List<Theme> growthAreas,
        List<Step> steps
) {

    public record Stage(
            String current,
            String next,
            String momentum,
            Integer nps,
            Integer npsDelta,
            Integer daysSinceLastTalk
    ) {
    }

    public record Theme(
            String title,
            int mentions,
            List<String> feedbackIds,
            List<Quote> quotes
    ) {
    }

    public record Quote(
            String feedbackId,
            String text
    ) {
    }

    public record Step(
            String title,
            String why,
            LocalDate dueDate,
            List<EvidenceRef> evidence
    ) {
    }

    public record EvidenceRef(
            String type,
            String id,
            String label
    ) {
    }
}
