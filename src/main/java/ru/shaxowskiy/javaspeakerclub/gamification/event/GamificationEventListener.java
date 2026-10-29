package ru.shaxowskiy.javaspeakerclub.gamification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.FeedbackCreated;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.ProfileCompletionChanged;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.TalkCreated;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.TalkDeleted;
import ru.shaxowskiy.javaspeakerclub.gamification.service.GamificationActivityService;

/**
 * Начисляет баллы после коммита основного сценария. Ошибки только логируются: доклад или отзыв уже сохранены.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GamificationEventListener {

    private final GamificationActivityService activityService;

    @TransactionalEventListener(fallbackExecution = true)
    public void onTalkCreated(TalkCreated event) {
        handle(event, () -> activityService.onTalkCreated(event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onTalkDeleted(TalkDeleted event) {
        handle(event, () -> activityService.onTalkDeleted(event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onFeedbackCreated(FeedbackCreated event) {
        handle(event, () -> activityService.onFeedbackCreated(event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onProfileCompletionChanged(ProfileCompletionChanged event) {
        handle(event, () -> activityService.onProfileCompletionChanged(event));
    }

    private static void handle(Object event, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Failed to award gamification points for {}", event, e);
        }
    }
}
