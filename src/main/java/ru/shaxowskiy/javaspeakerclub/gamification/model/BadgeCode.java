package ru.shaxowskiy.javaspeakerclub.gamification.model;

public enum BadgeCode {
    FIRST_TALK("Первый доклад", "Добавьте первый доклад на платформу", Category.ACTIVITY, false),
    TALKS_10("Десять докладов", "Наберите 10 докладов", Category.ACTIVITY, false),
    FIRST_FEEDBACK("Первый отзыв", "Получите первый отзыв слушателя", Category.ACTIVITY, false),
    FEEDBACK_50("Голос аудитории", "Получите 50 отзывов слушателей", Category.ACTIVITY, false),
    STREAK_7("Неделя в строю", "Отмечайтесь 7 дней подряд", Category.ACTIVITY, false),
    STREAK_30("Месяц в строю", "Отмечайтесь 30 дней подряд", Category.ACTIVITY, false),
    PROFILE_COMPLETE("Профиль на 100%", "Заполните профиль полностью", Category.ACTIVITY, false),
    POINTS_1000("Тысяча опыта", "Накопите 1000 баллов опыта", Category.ACTIVITY, false),
    REACHED_GOLD("Золото", "Поднимитесь в Золотую лигу", Category.LEAGUE, false),
    REACHED_PLATINUM("Платина", "Поднимитесь в Платиновую лигу", Category.LEAGUE, false),
    REACHED_DIAMOND("Бриллиант", "Поднимитесь в Бриллиантовую лигу", Category.LEAGUE, false),
    SEASON_WINNER("Победитель недели", "Займите первое место в своей лиге по итогам недели", Category.SEASON, true),
    SEASON_PODIUM("Пьедестал", "Войдите в тройку лучших своей лиги по итогам недели", Category.SEASON, true),
    PROMOTED("Повышение", "Перейдите в лигу выше по итогам недели", Category.SEASON, true);

    public enum Category {
        ACTIVITY,
        LEAGUE,
        SEASON
    }

    private final String title;
    private final String description;
    private final Category category;
    private final boolean repeatable;

    BadgeCode(String title, String description, Category category, boolean repeatable) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.repeatable = repeatable;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public boolean repeatable() {
        return repeatable;
    }

    /**
     * Значок за достижение лиги или null, если за эту лигу значка нет.
     */
    public static BadgeCode forReachedLeague(League league) {
        return switch (league) {
            case GOLD -> REACHED_GOLD;
            case PLATINUM -> REACHED_PLATINUM;
            case DIAMOND -> REACHED_DIAMOND;
            default -> null;
        };
    }
}
