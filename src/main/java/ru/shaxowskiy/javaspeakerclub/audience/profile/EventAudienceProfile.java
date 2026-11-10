package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.util.List;
import java.util.Map;

/**
 * Профиль ожидаемой аудитории после валидации.
 * primarySegment — преимущественная ЦА: schoolchildren, students, beginners, practitioners или experts;
 * сервер берёт сегмент с наибольшей долей в experience. segmentLabel и segmentReason — кто это и почему так решено.
 * experience — доли сегментов в процентах, в сумме 100. levels — доли уровней (junior, middle, senior, lead), в сумме 100;
 * пусто, если уровень не ясен. ageRange — только если возраст прямо следует из данных мероприятия.
 * visualExpectation: high, medium или low — насколько аудитории важна наглядность слайдов.
 * confidence: low, medium, high.
 */
public record EventAudienceProfile(
        String summary,
        String primarySegment,
        String segmentLabel,
        String segmentReason,
        Map<String, Integer> experience,
        AgeRange ageRange,
        String visualExpectation,
        Map<String, Integer> levels,
        List<String> roles,
        List<String> domains,
        List<String> expectations,
        List<String> assumedKnowledge,
        List<String> turnoffs,
        String confidence,
        List<Evidence> evidence
) {

    public record AgeRange(Integer from, Integer to) {
    }

    /**
     * source: EVENT (id — поле мероприятия), ACCEPTED_TALK, PAST_EDITION или DEVREL. label собирает сервер.
     */
    public record Evidence(String source, String id, String label) {
    }
}
