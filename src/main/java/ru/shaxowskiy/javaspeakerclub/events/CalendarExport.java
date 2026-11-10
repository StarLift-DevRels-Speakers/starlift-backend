package ru.shaxowskiy.javaspeakerclub.events;

import ru.shaxowskiy.javaspeakerclub.events.EventModels.ScheduleItemResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Выгрузка выступлений и мероприятий DevRel во внешний календарь: ссылка «Добавить в Google Календарь» и файл iCalendar (RFC 5545).
 * Мероприятия хранятся с датой без времени, поэтому событие календаря — на весь день.
 */
final class CalendarExport {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String GOOGLE_TEMPLATE = "https://calendar.google.com/calendar/render?action=TEMPLATE";
    private static final Map<String, String> FORMAT_LABELS = Map.of(
            "OFFLINE", "Офлайн",
            "ONLINE", "Онлайн",
            "HYBRID", "Гибрид"
    );
    /**
     * Напоминания календаря совпадают с баннером платформы: за неделю, за 3 дня, за день и в 9:00 в день выступления.
     */
    private static final List<String> ALARMS = List.of("-P7D", "-P3D", "-P1D", "PT9H");
    /**
     * Строки iCalendar длиннее 75 октетов переносятся.
     */
    private static final int LINE_LIMIT = 75;

    private CalendarExport() {
    }

    static String title(ScheduleItemResponse item) {
        return (isHosting(item) ? "Мероприятие: " : "Выступление: ") + item.eventName();
    }

    static String description(ScheduleItemResponse item) {
        return Stream.of(
                        (isHosting(item) ? "Тематика: " : "Доклад: ") + item.topic(),
                        "Формат: " + FORMAT_LABELS.getOrDefault(item.format(), item.format()),
                        item.ownerName() == null || isHosting(item) ? null : "Организатор (DevRel): " + item.ownerName(),
                        "Добавлено из StarLift"
                )
                .filter(line -> line != null)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Ссылка открывает форму нового события в Google Календаре с заполненными полями — без OAuth и доступа к аккаунту.
     */
    static String googleCalendarUrl(ScheduleItemResponse item) {
        return GOOGLE_TEMPLATE
                + "&text=" + encode(title(item))
                + "&dates=" + item.date().format(DAY) + "/" + item.date().plusDays(1).format(DAY)
                + "&details=" + encode(description(item))
                + (isBlank(item.city()) ? "" : "&location=" + encode(item.city()));
    }

    /**
     * Календарь с выступлениями; Google Календарь, Apple Calendar и Outlook импортируют его как есть.
     */
    static String ics(List<ScheduleItemResponse> items, LocalDateTime nowUtc) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//StarLift//Speaker schedule//RU");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + text("StarLift — мои мероприятия"));
        for (ScheduleItemResponse item : items) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + item.eventId() + "@starlift");
            line(out, "DTSTAMP:" + nowUtc.format(STAMP));
            line(out, "DTSTART;VALUE=DATE:" + item.date().format(DAY));
            line(out, "DTEND;VALUE=DATE:" + item.date().plusDays(1).format(DAY));
            line(out, "SUMMARY:" + text(title(item)));
            line(out, "DESCRIPTION:" + text(description(item)));
            if (!isBlank(item.city())) {
                line(out, "LOCATION:" + text(item.city()));
            }
            line(out, "TRANSP:OPAQUE");
            for (String trigger : ALARMS) {
                line(out, "BEGIN:VALARM");
                line(out, "ACTION:DISPLAY");
                line(out, "DESCRIPTION:" + text(title(item)));
                line(out, "TRIGGER:" + trigger);
                line(out, "END:VALARM");
            }
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    static String fileName(LocalDate date) {
        return "starlift-" + date.format(DAY) + ".ics";
    }

    static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Экранирование TEXT-значений iCalendar.
     */
    private static String text(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /**
     * Пишет строку с CRLF, перенося её по 75 октетов UTF-8 и не разрывая символы.
     */
    private static void line(StringBuilder out, String content) {
        int octets = 0;
        int limit = LINE_LIMIT;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append("\r\n ");
                octets = 0;
                // Пробел продолжения тоже считается в длину строки.
                limit = LINE_LIMIT - 1;
            }
            out.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }

    private static boolean isHosting(ScheduleItemResponse item) {
        return SpeakerScheduleService.KIND_HOSTING.equals(item.kind());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
