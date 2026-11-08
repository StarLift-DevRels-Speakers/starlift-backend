package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.util.List;
import java.util.Map;

/**
 * Правка профиля организатором. После неё профиль не пересчитывается, пока организатор не сбросит правку.
 * primarySegment обязателен по смыслу: без него сегмент берётся из уровней.
 */
public record EventAudienceProfileUpdate(
        String summary,
        String primarySegment,
        String segmentLabel,
        Integer ageFrom,
        Integer ageTo,
        String visualExpectation,
        Map<String, Object> levels,
        List<String> roles,
        List<String> domains,
        List<String> expectations,
        List<String> assumedKnowledge,
        List<String> turnoffs
) {
}
