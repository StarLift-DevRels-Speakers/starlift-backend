package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.util.List;
import java.util.Map;

/**
 * Сырой ответ LLM. Ничему из него не доверяем, пока не прошёл EventAudienceProfileValidator.
 */
public record EventAudienceProfileDraft(
        String summary,
        Map<String, Object> levels,
        List<String> roles,
        List<String> domains,
        List<String> expectations,
        List<String> assumedKnowledge,
        List<String> turnoffs,
        String confidence,
        List<Evidence> evidence
) {

    public record Evidence(String source, String id) {
    }
}
