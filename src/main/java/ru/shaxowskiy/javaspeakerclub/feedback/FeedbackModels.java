package ru.shaxowskiy.javaspeakerclub.feedback;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Модели публичной формы отзыва и отзывов спикера.
 */
public final class FeedbackModels {

    private FeedbackModels() {
    }

    /**
     * Ответы на пять вопросов формы, индексы 0–2. У отзывов, оставленных до формы, — null.
     */
    public record FeedbackAnswers(Integer utility, Integer clarity, Integer examples, Integer pace, Integer engagement) {
    }

    public record PublicFeedbackRequest(FeedbackAnswers answers, String comment) {
    }

    /**
     * Что видит слушатель: только выступление, без профиля спикера.
     */
    public record PublicFeedbackFormResponse(String topic, String eventName, LocalDate date) {
    }

    public record FeedbackLinkResponse(UUID talkId, String token) {
    }

    public record SpeakerFeedbackResponse(
            UUID id,
            UUID talkId,
            LocalDateTime createdDate,
            int rating,
            FeedbackAnswers answers,
            String comment,
            String positive,
            String negative,
            String improvementSuggestions
    ) {
    }
}
