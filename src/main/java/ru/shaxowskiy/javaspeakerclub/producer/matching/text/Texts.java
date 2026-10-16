package ru.shaxowskiy.javaspeakerclub.producer.matching.text;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Общие формулировки для объяснений: даты, склонения, названия форматов и уровней. */
public final class Texts {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private Texts() {
    }

    public static String date(LocalDate date) {
        return date == null ? "дата неизвестна" : DATE.format(date);
    }

    public static String plural(long count, String one, String few, String many) {
        long mod100 = Math.abs(count) % 100;
        long mod10 = mod100 % 10;
        String word;
        if (mod100 >= 11 && mod100 <= 14) {
            word = many;
        } else if (mod10 == 1) {
            word = one;
        } else if (mod10 >= 2 && mod10 <= 4) {
            word = few;
        } else {
            word = many;
        }
        return count + " " + word;
    }

    public static String talks(long count) {
        return plural(count, "доклад", "доклада", "докладов");
    }

    public static String quoted(List<String> values) {
        return values.stream().map(value -> "«" + value + "»").collect(Collectors.joining(", "));
    }

    public static String stage(String stage) {
        if (stage == null) {
            return "не указан";
        }
        return switch (stage.toLowerCase(Locale.ROOT)) {
            case "local" -> "локальный";
            case "regional" -> "региональный";
            case "federal" -> "федеральный";
            case "international" -> "международный";
            default -> stage;
        };
    }

    public static String format(String format) {
        if (format == null) {
            return "формат не указан";
        }
        return switch (format.toUpperCase(Locale.ROOT)) {
            case "OFFLINE" -> "офлайн";
            case "ONLINE" -> "онлайн";
            case "HYBRID" -> "гибрид";
            default -> format.toLowerCase(Locale.ROOT);
        };
    }

    public static String conferenceType(String type) {
        if (type == null) {
            return "мероприятие";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CONFERENCE" -> "конференция";
            case "MEETUP" -> "митап";
            default -> type.toLowerCase(Locale.ROOT);
        };
    }

    public static String rating(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }

    public static String capitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
