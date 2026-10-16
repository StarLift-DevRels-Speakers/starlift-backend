package ru.shaxowskiy.javaspeakerclub.producer.model;

import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * status: ready, insufficient_data или failed. plan и validation есть только у ready.
 * confidence — насколько план опирается на достаточные данные; gaps — чего не хватает в профиле и данных спикера.
 */
public record ProducerPlanResponse(
        String id,
        Long speakerId,
        String status,
        String message,
        ProducerPlan plan,
        PlanValidation validation,
        Recommendation.Confidence confidence,
        List<String> gaps,
        String model,
        String promptVersion,
        LocalDateTime createdDate
) {
}
