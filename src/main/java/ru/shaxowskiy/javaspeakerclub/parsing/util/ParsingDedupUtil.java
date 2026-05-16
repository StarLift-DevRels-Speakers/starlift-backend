package ru.shaxowskiy.javaspeakerclub.parsing.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public final class ParsingDedupUtil {

    private ParsingDedupUtil() {
    }

    public static String dedupKey(String conference, String speakerName, LocalDate eventDate, String sourceUrl) {
        String raw = String.join("|",
                normalize(conference),
                normalize(speakerName),
                eventDate.toString(),
                normalize(sourceUrl));
        return sha256Hex(raw);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
