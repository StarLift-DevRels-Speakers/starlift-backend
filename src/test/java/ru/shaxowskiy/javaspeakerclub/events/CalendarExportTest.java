package ru.shaxowskiy.javaspeakerclub.events;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.ScheduleItemResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Этапы напоминаний и форматы выгрузки в календарь — без контекста Spring.
 */
class CalendarExportTest {

    @Test
    void reminderStagesFollowDaysLeft() {
        assertThat(SpeakerScheduleService.reminder(8)).isNull();
        assertThat(SpeakerScheduleService.reminder(7)).isEqualTo("WEEK");
        assertThat(SpeakerScheduleService.reminder(4)).isEqualTo("WEEK");
        assertThat(SpeakerScheduleService.reminder(3)).isEqualTo("THREE_DAYS");
        assertThat(SpeakerScheduleService.reminder(2)).isEqualTo("THREE_DAYS");
        assertThat(SpeakerScheduleService.reminder(1)).isEqualTo("ONE_DAY");
        assertThat(SpeakerScheduleService.reminder(0)).isEqualTo("TODAY");
        assertThat(SpeakerScheduleService.reminder(-1)).isNull();
    }

    @Test
    void icsIsAllDayEscapedAndFolded() {
        ScheduleItemResponse item = new ScheduleItemResponse(
                "SPEECH",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Highload; Java, Kotlin и очень длинное название конференции, которое не помещается в строку",
                LocalDate.of(2026, 11, 17),
                "Екатеринбург",
                "HYBRID",
                "Доклад",
                "Дина Релова",
                7,
                "WEEK",
                null
        );

        String ics = CalendarExport.ics(List.of(item), LocalDateTime.of(2026, 11, 10, 8, 30));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("DTSTART;VALUE=DATE:20261117\r\n", "DTEND;VALUE=DATE:20261118\r\n", "DTSTAMP:20261110T083000Z");
        assertThat(ics).contains("TRIGGER:-P7D", "TRIGGER:-P3D", "TRIGGER:-P1D", "TRIGGER:PT9H", "LOCATION:Екатеринбург");
        assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:Выступление: Highload\\; Java\\, Kotlin и очень длинное");
        assertThat(ics.replace("\r\n ", "")).contains("Формат: Гибрид\\nОрганизатор (DevRel): Дина Релова");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }

        assertThat(CalendarExport.googleCalendarUrl(item))
                .contains("&dates=20261117/20261118")
                .contains("&location=%D0%95")
                .doesNotContain("+");
    }

    @Test
    void icsWithoutSpeechesIsAnEmptyCalendar() {
        String ics = CalendarExport.ics(List.of(), LocalDateTime.of(2026, 11, 10, 0, 0));

        assertThat(ics).contains("BEGIN:VCALENDAR", "END:VCALENDAR").doesNotContain("BEGIN:VEVENT");
    }

    @Test
    void cityIsOptional() {
        ScheduleItemResponse online = new ScheduleItemResponse("SPEECH", UUID.randomUUID(), "Online Day", LocalDate.of(2026, 12, 31),
                null, "ONLINE", "Доклад", null, 30, null, null);

        assertThat(CalendarExport.googleCalendarUrl(online)).doesNotContain("&location=").contains("&dates=20261231/20270101");
        assertThat(CalendarExport.ics(List.of(online), LocalDateTime.of(2026, 11, 10, 0, 0)))
                .doesNotContain("LOCATION:")
                .doesNotContain("Организатор");
    }

    @Test
    void hostingIsTitledAsEventWithoutOrganizerLine() {
        ScheduleItemResponse hosting = new ScheduleItemResponse("HOSTING", UUID.randomUUID(), "Backend Meetup", LocalDate.of(2026, 11, 17),
                "Москва", "OFFLINE", "Backend", "Дина Релова", 7, "WEEK", null);

        String ics = CalendarExport.ics(List.of(hosting), LocalDateTime.of(2026, 11, 10, 0, 0)).replace("\r\n ", "");

        assertThat(ics).contains("SUMMARY:Мероприятие: Backend Meetup", "Тематика: Backend").doesNotContain("Организатор");
    }
}
