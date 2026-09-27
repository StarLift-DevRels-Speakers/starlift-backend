package ru.shaxowskiy.javaspeakerclub.producer.model;

import java.time.LocalDateTime;

/**
 * status: ready, insufficient_data или failed. plan и validation есть только у ready.
 */
public record ProducerPlanResponse(
        String id,
        Long speakerId,
        String status,
        String message,
        ProducerPlan plan,
        PlanValidation validation,
        String model,
        String promptVersion,
        LocalDateTime createdDate
) {
}
