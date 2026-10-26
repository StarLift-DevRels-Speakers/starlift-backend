package ru.shaxowskiy.javaspeakerclub.gamification.event;

import java.util.UUID;

/**
 * События платформы, за которые спикеру начисляются баллы. Публикуются в транзакции основного сценария,
 * обрабатываются после её коммита.
 */
public final class GamificationEvents {

    private GamificationEvents() {
    }

    public record TalkCreated(UUID talkId, Long speakerId) {
    }

    public record TalkDeleted(UUID talkId, Long speakerId) {
    }

    public record FeedbackCreated(UUID feedbackId, UUID talkId, Long speakerId, int rating) {
    }

    public record ProfileCompletionChanged(Long speakerId, int profileCompletion) {
    }
}
