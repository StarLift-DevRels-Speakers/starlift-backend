package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.util.List;
import java.util.Map;

/**
 * Профиль ожидаемой аудитории после валидации.
 * levels — доля уровней в процентах (junior, middle, senior, lead), в сумме 100; пусто, если уровень не ясен.
 * confidence: low, medium, high; manual — профиль уточнил организатор.
 */
public record EventAudienceProfile(
        String summary,
        Map<String, Integer> levels,
        List<String> roles,
        List<String> domains,
        List<String> expectations,
        List<String> assumedKnowledge,
        List<String> turnoffs,
        String confidence,
        List<Evidence> evidence
) {

    /**
     * source: EVENT (id — поле мероприятия), ACCEPTED_TALK, PAST_EDITION или DEVREL. label собирает сервер.
     */
    public record Evidence(String source, String id, String label) {
    }
}
