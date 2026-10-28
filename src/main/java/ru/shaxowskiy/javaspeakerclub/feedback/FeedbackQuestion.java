package ru.shaxowskiy.javaspeakerclub.feedback;

import java.util.List;

/**
 * Пять вопросов публичной формы отзыва. Подписи совпадают с фронтендом (features/feedback/questions.ts):
 * ответ — индекс варианта, 0 — лучший.
 */
public enum FeedbackQuestion {
    UTILITY(List.of("Очень полезно", "Скорее полезно", "Мало полезной информации")),
    CLARITY(List.of("Все понятно", "Иногда было сложно", "Материал был непонятен")),
    EXAMPLES(List.of("Да, примеров достаточно", "Хотелось бы больше", "Практических примеров не было")),
    PACE(List.of("Комфортный", "Слишком быстрый", "Слишком медленный")),
    ENGAGEMENT(List.of("Очень интересно", "Местами интересно", "Было сложно удерживать внимание"));

    private final List<String> options;

    FeedbackQuestion(List<String> options) {
        this.options = options;
    }

    public String label(int answer) {
        return options.get(answer);
    }
}
