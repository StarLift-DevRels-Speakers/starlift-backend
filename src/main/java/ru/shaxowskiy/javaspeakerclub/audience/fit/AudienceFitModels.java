package ru.shaxowskiy.javaspeakerclub.audience.fit;

import java.time.LocalDateTime;
import java.util.UUID;

public final class AudienceFitModels {

    private AudienceFitModels() {
    }

    /**
     * Проверить доклад (talkId) и/или файл (mediaId) под мероприятие. Без mediaId берётся последний файл доклада.
     */
    public record AudienceFitRequest(UUID eventId, UUID talkId, UUID mediaId) {
    }

    /**
     * trigger: manual, upload или application. status: pending, ready, insufficient_data или failed.
     */
    public record AudienceFitCheckResponse(
            UUID id,
            UUID eventId,
            String eventName,
            Long speakerId,
            UUID talkId,
            String talkName,
            UUID mediaId,
            String fileName,
            String trigger,
            String status,
            String message,
            AudienceFitResult result,
            LocalDateTime createdDate,
            LocalDateTime updatedDate
    ) {
    }
}
