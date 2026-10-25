package ru.shaxowskiy.javaspeakerclub.signals.model;

/**
 * Ответ weak-signals: score 1–5, confidence — low, medium или high.
 */
public record TalentScoreResponse(
        Long id,
        int score,
        String reason,
        String confidence
) {
}
