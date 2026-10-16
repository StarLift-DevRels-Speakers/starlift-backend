package ru.shaxowskiy.javaspeakerclub.parsing.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerRow;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ParsedSpeakerRowValidator {

    private static final Set<String> PLACEHOLDER_NAMES = Set.of(
            "null",
            "неизвестен",
            "неизвестно",
            "unknown",
            "n/a",
            "na",
            "tbd",
            "—",
            "-",
            "нет данных",
            "не указан",
            "не указано"
    );

    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile(
            ".*[\\p{L}].*[\\p{L}].*"
    );

    public boolean isValid(ParsedSpeakerRow row) {
        if (row == null) {
            return false;
        }
        String speakerName = normalize(row.speakerName());
        if (speakerName == null) {
            return false;
        }
        if (PLACEHOLDER_NAMES.contains(speakerName.toLowerCase(Locale.ROOT))) {
            log.debug("Rejected placeholder speaker name: {}", speakerName);
            return false;
        }
        if (!looksLikePersonName(speakerName)) {
            log.debug("Rejected non-person speaker name: {}", speakerName);
            return false;
        }
        String conference = normalize(row.conference());
        if (conference == null) {
            return false;
        }
        if (speakerName.equalsIgnoreCase(conference)) {
            log.debug("Rejected speaker name equal to conference: {}", speakerName);
            return false;
        }
        return true;
    }

    public ParsedSpeakerRow normalizeRow(ParsedSpeakerRow row) {
        if (row == null) {
            return null;
        }
        return new ParsedSpeakerRow(
                normalize(row.conference()),
                normalize(row.speakerName()),
                normalizeCompany(row.company())
        );
    }

    private static String normalizeCompany(String company) {
        String normalized = normalize(company);
        if (normalized == null) {
            return null;
        }
        if (PLACEHOLDER_NAMES.contains(normalized.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean looksLikePersonName(String name) {
        if (name.length() < 3) {
            return false;
        }
        if (!PERSON_NAME_PATTERN.matcher(name).matches()) {
            return false;
        }
        return name.contains(" ") || name.contains(".") || name.contains("-");
    }
}
