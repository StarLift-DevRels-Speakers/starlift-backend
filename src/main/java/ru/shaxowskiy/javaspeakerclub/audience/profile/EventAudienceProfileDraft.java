package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.util.List;
import java.util.Map;

/**
 * Сырой ответ LLM. Ничему из него не доверяем, пока не прошёл EventAudienceProfileValidator.
 */
public record EventAudienceProfileDraft(
        String summary,
        String primarySegment,
        String segmentLabel,
        String segmentReason,
        Map<String, Object> experience,
        Age age,
        String visualExpectation,
        Map<String, Object> levels,
        List<String> roles,
        List<String> domains,
        List<String> expectations,
        List<String> assumedKnowledge,
        List<String> turnoffs,
        String confidence,
        List<Evidence> evidence
) {

    /**
     * Возраст с полем мероприятия, где он назван (name, topic, audience или description).
     */
    public record Age(Object from, Object to, String sourceField) {
    }

    public record Evidence(String source, String id) {
    }
}
