package ru.shaxowskiy.javaspeakerclub.audience.fit;

import java.util.List;

/**
 * Итог проверки после валидации. verdict: fits, partially или not_fits — по итоговой оценке score (0–100),
 * которую считает сервер из оценок критериев. Цитаты и подписи фрагментов берутся из материала, а не у модели.
 */
public record AudienceFitResult(
        String verdict,
        int score,
        String summary,
        List<Criterion> criteria,
        List<Issue> issues,
        List<Strength> strengths,
        String profileConfidence,
        boolean truncated
) {

    public record Criterion(String key, String label, int score, String comment) {
    }

    public record Issue(String fragmentId, String label, String quote, String problem, String fix) {
    }

    public record Strength(String fragmentId, String label, String quote, String why) {
    }
}
