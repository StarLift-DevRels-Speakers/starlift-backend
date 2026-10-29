package ru.shaxowskiy.javaspeakerclub.producer.llm;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Небольшие модели иногда обрывают JSON на последней скобке: {"items": [...]. Дописываем недостающие
 * закрывающие скобки по стеку открытых; содержимое ответа не меняется и дальше проверяется как обычно.
 */
public final class JsonRepair {

    private JsonRepair() {
    }

    /** Текст от первой «{» до конца с дописанными закрывающими скобками; null — если «{» нет. */
    public static String closeUnbalanced(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf('{');
        if (start < 0) {
            return null;
        }
        String text = content.substring(start).replaceAll("\\s*```\\s*$", "").trim();
        Deque<Character> open = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> open.push('}');
                case '[' -> open.push(']');
                case '}', ']' -> {
                    if (open.isEmpty() || open.peek() != c) {
                        return null;
                    }
                    open.pop();
                    if (open.isEmpty()) {
                        return text.substring(0, i + 1);
                    }
                }
                default -> {
                }
            }
        }
        if (inString) {
            return null;
        }
        StringBuilder repaired = new StringBuilder(text);
        while (!open.isEmpty()) {
            repaired.append(open.pop());
        }
        return repaired.toString();
    }
}
