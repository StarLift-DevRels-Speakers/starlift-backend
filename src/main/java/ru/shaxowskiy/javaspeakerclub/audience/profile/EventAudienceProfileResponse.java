package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * status: ready или failed (profile = null, message объясняет).
 */
public record EventAudienceProfileResponse(
        UUID eventId,
        String status,
        String message,
        EventAudienceProfile profile,
        boolean manual,
        String model,
        String promptVersion,
        LocalDateTime updatedDate
) {
}
