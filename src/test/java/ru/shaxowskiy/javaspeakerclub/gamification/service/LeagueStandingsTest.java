package ru.shaxowskiy.javaspeakerclub.gamification.service;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.gamification.GamificationProperties;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.model.LeagueZone;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeagueStandings.Contender;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeagueStandings.Standing;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueStandingsTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 26, 10, 0);

    private final GamificationProperties properties = new GamificationProperties(
            "Europe/Moscow", false, 2, 2, 5, 9, 7, 200, 1000,
            new GamificationProperties.Points(50, 10, 15, 100, 5, 25));

    @Test
    void ordersByPointsThenByWhoScoredFirst() {
        List<Standing> standings = LeagueStandings.rank(League.SILVER, List.of(
                new Contender(1L, 40, T0.plusHours(2)),
                new Contender(2L, 40, T0.plusHours(1)),
                new Contender(3L, 90, T0.plusHours(3)),
                new Contender(4L, 0, null)
        ), properties);

        assertThat(standings).extracting(Standing::speakerId).containsExactly(3L, 2L, 1L, 4L);
        assertThat(standings).extracting(Standing::rank).containsExactly(1, 2, 3, 4);
    }

    @Test
    void marksPromotionAndDemotionZones() {
        List<Standing> standings = LeagueStandings.rank(League.SILVER, contenders(100, 80, 60, 40, 20, 0), properties);

        assertThat(standings).extracting(Standing::zone).containsExactly(
                LeagueZone.PROMOTION, LeagueZone.PROMOTION,
                LeagueZone.SAFE, LeagueZone.SAFE,
                LeagueZone.DEMOTION, LeagueZone.DEMOTION);
    }

    @Test
    void speakerWithoutPointsIsNotPromoted() {
        List<Standing> standings = LeagueStandings.rank(League.BRONZE, contenders(30, 0, 0), properties);

        assertThat(standings).extracting(Standing::zone)
                .containsExactly(LeagueZone.PROMOTION, LeagueZone.SAFE, LeagueZone.SAFE);
    }

    @Test
    void smallLeagueHasNoDemotion() {
        List<Standing> standings = LeagueStandings.rank(League.GOLD, contenders(50, 40, 30, 20), properties);

        assertThat(standings).extracting(Standing::zone).doesNotContain(LeagueZone.DEMOTION);
        assertThat(LeagueStandings.demotionCount(League.GOLD, 4, properties)).isZero();
    }

    @Test
    void edgeLeaguesDoNotPromoteOrDemoteBeyondBounds() {
        List<Standing> diamond = LeagueStandings.rank(League.DIAMOND, contenders(100, 80, 60, 40, 20, 10), properties);
        List<Standing> bronze = LeagueStandings.rank(League.BRONZE, contenders(100, 80, 60, 40, 20, 10), properties);

        assertThat(diamond).extracting(Standing::zone).doesNotContain(LeagueZone.PROMOTION);
        assertThat(bronze).extracting(Standing::zone).doesNotContain(LeagueZone.DEMOTION);
    }

    private static List<Contender> contenders(int... points) {
        return LongStream.range(0, points.length)
                .mapToObj(i -> new Contender(i + 1, points[(int) i], points[(int) i] > 0 ? T0.plusMinutes(i) : null))
                .toList();
    }
}
