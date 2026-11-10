package ru.shaxowskiy.javaspeakerclub.audience.profile;

import java.time.LocalDate;
import java.util.List;

/**
 * Всё, из чего выводится ожидаемая аудитория: поля мероприятия, принятые доклады программы
 * и спикеры прошлых выпусков конференции из парсинга.
 */
public record EventAudienceInputs(
        EventInfo event,
        List<AcceptedTalk> acceptedTalks,
        List<PastEdition> pastEditions
) {

    public record EventInfo(
            String name,
            LocalDate date,
            String city,
            String format,
            String topic,
            String audience,
            String description
    ) {
    }

    public record AcceptedTalk(String id, String name, String topic, String description) {
    }

    public record PastEdition(String id, String conference, String speakerName, String company, LocalDate eventDate) {
    }
}
