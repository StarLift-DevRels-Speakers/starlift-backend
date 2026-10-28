package ru.shaxowskiy.javaspeakerclub.gamification.service;

import ru.shaxowskiy.javaspeakerclub.gamification.GamificationProperties;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.model.LeagueZone;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Правила турнирной таблицы лиги: порядок мест и зоны повышения/понижения.
 */
public final class LeagueStandings {

    /**
     * Баллы по убыванию; при равенстве выше тот, кто набрал их раньше; спикер без начислений — ниже; затем по id.
     */
    public static final Comparator<Contender> ORDER = Comparator
            .comparingInt(Contender::points).reversed()
            .thenComparing(Contender::lastEarnedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Contender::speakerId);

    private LeagueStandings() {
    }

    public record Contender(Long speakerId, int points, LocalDateTime lastEarnedAt) {
    }

    public record Standing(Long speakerId, int rank, int points, LeagueZone zone) {
    }

    public static int promotionCount(League league, GamificationProperties properties) {
        return league.isHighest() ? 0 : properties.promotionCount();
    }

    public static int demotionCount(League league, int leagueSize, GamificationProperties properties) {
        if (league.isLowest() || leagueSize < properties.minParticipantsForDemotion()) {
            return 0;
        }
        return properties.demotionCount();
    }

    public static List<Standing> rank(League league, List<Contender> contenders, GamificationProperties properties) {
        List<Contender> sorted = contenders.stream().sorted(ORDER).toList();
        int size = sorted.size();
        int promotion = promotionCount(league, properties);
        int demotion = demotionCount(league, size, properties);

        List<Standing> standings = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Contender contender = sorted.get(i);
            int rank = i + 1;
            LeagueZone zone;
            if (rank <= promotion && contender.points() > 0) {
                zone = LeagueZone.PROMOTION;
            } else if (rank > size - demotion) {
                zone = LeagueZone.DEMOTION;
            } else {
                zone = LeagueZone.SAFE;
            }
            standings.add(new Standing(contender.speakerId(), rank, contender.points(), zone));
        }
        return standings;
    }
}
