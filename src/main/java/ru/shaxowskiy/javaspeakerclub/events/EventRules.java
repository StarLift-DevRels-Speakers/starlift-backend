package ru.shaxowskiy.javaspeakerclub.events;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Общие правила мероприятий: даты CFP и прошедших событий, имена пользователей, ошибки бизнес-правил.
 */
final class EventRules {

    private EventRules() {
    }

    /**
     * CFP принимает заявки до конца дня дедлайна.
     */
    static boolean isCfpOpen(EventsRecord event, LocalDate today) {
        return event.getCfpDeadline() != null && !event.getCfpDeadline().isBefore(today);
    }

    /**
     * День мероприятия прошёл: приглашения и решения по заявкам больше не имеют смысла.
     */
    static boolean isPast(EventsRecord event, LocalDate today) {
        return event.getEventDate().isBefore(today);
    }

    static String displayName(UsersRecord user) {
        if (user == null) {
            return null;
        }
        String full = Stream.of(user.getFirstName(), user.getLastName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return full.isBlank() ? user.getUsername() : full;
    }

    /**
     * Нарушено бизнес-правило: действие невозможно в текущем состоянии.
     */
    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
