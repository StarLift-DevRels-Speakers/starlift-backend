package ru.shaxowskiy.javaspeakerclub.events;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.ScheduleItemResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Предстоящие выступления текущего спикера с напоминаниями и выгрузкой в календарь.
 */
@RestController
@RequiredArgsConstructor
public class SpeakerScheduleController {

    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final SpeakerScheduleService scheduleService;

    @GetMapping("/api/me/schedule")
    public List<ScheduleItemResponse> schedule() {
        return scheduleService.findMine();
    }

    @GetMapping("/api/me/schedule/calendar.ics")
    public ResponseEntity<String> calendar() {
        return ics(scheduleService.calendar());
    }

    @GetMapping("/api/me/schedule/{eventId}/calendar.ics")
    public ResponseEntity<String> eventCalendar(@PathVariable UUID eventId) {
        return ics(scheduleService.calendar(eventId));
    }

    private static ResponseEntity<String> ics(String body) {
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(CalendarExport.fileName(LocalDate.now()))
                        .build()
                        .toString())
                .body(body);
    }
}
