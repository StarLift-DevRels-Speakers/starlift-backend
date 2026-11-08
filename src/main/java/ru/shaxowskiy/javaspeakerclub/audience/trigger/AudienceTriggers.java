package ru.shaxowskiy.javaspeakerclub.audience.trigger;

import java.util.UUID;

/**
 * События, на которые реагирует проверка под аудиторию. Публикуются в транзакции и обрабатываются в фоне после коммита.
 */
public final class AudienceTriggers {

    private AudienceTriggers() {
    }

    /**
     * Мероприятие опубликовано или изменилось — профиль аудитории стоит пересчитать заранее.
     */
    public record EventChanged(UUID eventId) {
    }

    /**
     * Спикер загрузил файл к докладу — проверить его под мероприятия, куда доклад подан.
     */
    public record MediaUploaded(Long speakerId, UUID mediaId, UUID talkId) {
    }

    /**
     * Доклад подан на мероприятие или заменён в заявке.
     */
    public record ApplicationTalkSet(Long speakerId, UUID eventId, UUID talkId) {
    }

    /**
     * Спикер сам запустил проверку.
     */
    public record FitCheckRequested(UUID checkId) {
    }
}
