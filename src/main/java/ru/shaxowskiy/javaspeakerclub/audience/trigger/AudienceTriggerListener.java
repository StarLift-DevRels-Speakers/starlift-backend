package ru.shaxowskiy.javaspeakerclub.audience.trigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitService;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfileService;
import ru.shaxowskiy.javaspeakerclub.audience.trigger.AudienceTriggers.ApplicationTalkSet;
import ru.shaxowskiy.javaspeakerclub.audience.trigger.AudienceTriggers.EventChanged;
import ru.shaxowskiy.javaspeakerclub.audience.trigger.AudienceTriggers.FitCheckRequested;
import ru.shaxowskiy.javaspeakerclub.audience.trigger.AudienceTriggers.MediaUploaded;

/**
 * Обработка в отдельном потоке после коммита: запрос пользователя не ждёт LLM, а фоновые записи
 * идут в своих соединениях, а не в уже завершённой транзакции.
 * Автоматические триггеры отключаются app.audience.triggers-enabled; ручной запуск работает всегда.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceTriggerListener {

    private final EventAudienceProfileService profileService;
    private final AudienceFitService fitService;
    private final AudienceProperties properties;

    @Async("audienceTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onEventChanged(EventChanged event) {
        if (properties.triggersEnabled()) {
            profileService.warmUp(event.eventId());
        }
    }

    @Async("audienceTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onMediaUploaded(MediaUploaded event) {
        if (!properties.triggersEnabled()) {
            return;
        }
        try {
            fitService.checkUploadedMedia(event.speakerId(), event.mediaId(), event.talkId());
        } catch (RuntimeException e) {
            log.warn("Audience fit for uploaded mediaId={} failed: {}", event.mediaId(), e.getMessage());
        }
    }

    @Async("audienceTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onApplicationTalkSet(ApplicationTalkSet event) {
        if (!properties.triggersEnabled()) {
            return;
        }
        try {
            fitService.checkApplication(event.speakerId(), event.eventId(), event.talkId());
        } catch (RuntimeException e) {
            log.warn("Audience fit for application eventId={} talkId={} failed: {}", event.eventId(), event.talkId(), e.getMessage());
        }
    }

    @Async("audienceTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onFitCheckRequested(FitCheckRequested event) {
        fitService.run(event.checkId());
    }
}
