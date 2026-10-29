package ru.shaxowskiy.javaspeakerclub.gamification.model;

/**
 * Лиги от низшей к высшей; порядок объявления задаёт порядок повышения.
 */
public enum League {
    BRONZE("Бронзовая лига"),
    SILVER("Серебряная лига"),
    GOLD("Золотая лига"),
    PLATINUM("Платиновая лига"),
    DIAMOND("Бриллиантовая лига");

    private final String title;

    League(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public int order() {
        return ordinal() + 1;
    }

    public boolean isLowest() {
        return this == values()[0];
    }

    public boolean isHighest() {
        return this == values()[values().length - 1];
    }

    public League up() {
        return isHighest() ? this : values()[ordinal() + 1];
    }

    public League down() {
        return isLowest() ? this : values()[ordinal() - 1];
    }

    public static League ofNullable(String code) {
        return code == null ? BRONZE : valueOf(code);
    }
}
