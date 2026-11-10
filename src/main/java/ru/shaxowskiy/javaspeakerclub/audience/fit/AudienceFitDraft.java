package ru.shaxowskiy.javaspeakerclub.audience.fit;

import java.util.List;

/**
 * Сырой ответ LLM. Ничему из него не доверяем, пока не прошёл AudienceFitValidator.
 */
public record AudienceFitDraft(
        String verdict,
        String summary,
        List<Criterion> criteria,
        List<Issue> issues,
        List<Strength> strengths
) {

    /**
     * score — 0–10; модель иногда пишет его строкой.
     */
    public record Criterion(String key, Object score, String comment) {
    }

    public record Issue(String fragmentId, String problem, String fix) {
    }

    public record Strength(String fragmentId, String why) {
    }
}
