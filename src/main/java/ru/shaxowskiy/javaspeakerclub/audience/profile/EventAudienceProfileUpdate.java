package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.util.List;
import java.util.Map;

/**
 * Правка профиля организатором. После неё профиль не пересчитывается, пока организатор не сбросит правку.
 */
public record EventAudienceProfileUpdate(
        String summary,
        Map<String, Object> levels,
        List<String> roles,
        List<String> domains,
        List<String> expectations,
        List<String> assumedKnowledge,
        List<String> turnoffs
) {
}
