package ru.shaxowskiy.javaspeakerclub.producer.llm;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Починка типичных огрехов JSON от LLM; содержимое ответа не меняется и дальше проверяется как обычно.
 * Небольшие модели иногда обрывают JSON на последней скобке: {"items": [...] — дописываем недостающие
 * закрывающие скобки по стеку открытых. Ещё модель пишет кавычки внутри строки без экранирования:
 * "comment": "Слайд "Наш продукт": реклама" — такие кавычки экранируем.
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

    /**
     * Экранирует двойные кавычки внутри строк. Кавычка закрывает строку-ключ, только если за ней идёт «:»,
     * а строку-значение — если за ней «}», «]» или «,» с началом следующего элемента; иначе это кавычка в тексте.
     * null — если «{» нет.
     */
    public static String escapeInnerQuotes(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf('{');
        if (start < 0) {
            return null;
        }
        String text = content.substring(start);
        StringBuilder out = new StringBuilder(text.length() + 16);
        // Для каждого открытого контейнера: true — объект, false — массив.
        Deque<Boolean> containers = new ArrayDeque<>();
        boolean expectKey = false;
        boolean inString = false;
        boolean stringIsKey = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    if (closesString(text, i + 1, stringIsKey, Boolean.TRUE.equals(containers.peek()))) {
                        inString = false;
                        if (stringIsKey) {
                            expectKey = false;
                        }
                    } else {
                        out.append('\\');
                    }
                }
                out.append(c);
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    stringIsKey = Boolean.TRUE.equals(containers.peek()) && expectKey;
                }
                case '{' -> {
                    containers.push(true);
                    expectKey = true;
                }
                case '[' -> containers.push(false);
                case '}', ']' -> {
                    if (!containers.isEmpty()) {
                        containers.pop();
                    }
                    expectKey = false;
                }
                case ',' -> expectKey = Boolean.TRUE.equals(containers.peek());
                default -> {
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean closesString(String text, int from, boolean key, boolean inObject) {
        int next = skipSpaces(text, from);
        if (next >= text.length()) {
            return true;
        }
        char c = text.charAt(next);
        if (key) {
            return c == ':';
        }
        if (c == '}' || c == ']') {
            return true;
        }
        if (c != ',') {
            return false;
        }
        int after = skipSpaces(text, next + 1);
        if (after >= text.length()) {
            return true;
        }
        char following = text.charAt(after);
        // В объекте за запятой идёт ключ, в массиве — любое значение.
        return inObject ? following == '"' : "\"{[-0123456789tfn".indexOf(following) >= 0;
    }

    private static int skipSpaces(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }
}
