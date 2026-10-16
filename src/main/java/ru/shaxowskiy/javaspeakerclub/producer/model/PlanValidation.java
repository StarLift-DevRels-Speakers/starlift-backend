package ru.shaxowskiy.javaspeakerclub.producer.model;

import java.util.List;

/**
 * Что валидатор принял и что отбросил. Отброшенное видно в отчёте прогона.
 */
public record PlanValidation(
        int acceptedThemes,
        int acceptedSteps,
        List<Rejection> rejections
) {

    public record Rejection(
            String item,
            String reason
    ) {
    }
}
