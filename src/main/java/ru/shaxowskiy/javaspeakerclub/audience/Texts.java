package ru.shaxowskiy.javaspeakerclub.audience;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class Texts {

    private Texts() {
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1).trim() + "…";
    }

    /**
     * Схлопывает пробелы и пустые строки, которые оставляет извлечение текста из PDF и слайдов.
     */
    public static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(' ', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Непустые строки без повторов, не длиннее maxLength, не больше maxItems.
     */
    public static List<String> cleanList(List<String> values, int maxItems, int maxLength) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values.stream().filter(Objects::nonNull).toList()) {
            String trimmed = trimToNull(value);
            if (trimmed != null && unique.stream().noneMatch(existing -> existing.equalsIgnoreCase(trimmed))) {
                unique.add(truncate(trimmed, maxLength));
            }
        }
        return List.copyOf(new ArrayList<>(unique).subList(0, Math.min(maxItems, unique.size())));
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Число из ответа модели: 7, 7.5, "7", "70%". Не число — null.
     */
    public static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            String cleaned = text.replace("%", "").replace(',', '.').trim();
            try {
                return Double.parseDouble(cleaned);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
