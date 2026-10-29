package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.gamification.GamificationProperties;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.GlobalLeaderboardResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeaderboardEntry;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeaderboardPeriod;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeagueLeaderboardResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeagueResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.model.LeagueZone;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.PointTransactionRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.PointTransactionRepository.PointsTotal;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeagueStandings.Contender;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeagueStandings.Standing;
import ru.shaxowskiy.javaspeakerclub.gamification.service.SpeakerDirectory.Participant;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationSeasonsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final SpeakerDirectory speakerDirectory;
    private final SeasonService seasonService;
    private final PointTransactionRepository transactionRepository;
    private final GamificationProperties properties;

    /**
     * Таблица одной лиги за сезон.
     */
    public record LeagueTable(League league, List<Standing> standings, int promotionCount, int demotionCount) {
    }

    @Transactional
    public List<LeagueResponse> leagues() {
        Map<League, Long> sizes = speakerDirectory.participants().stream()
                .collect(Collectors.groupingBy(Participant::league, Collectors.counting()));
        return Arrays.stream(League.values())
                .map(league -> {
                    int size = sizes.getOrDefault(league, 0L).intValue();
                    return new LeagueResponse(
                            league,
                            league.title(),
                            league.order(),
                            size,
                            LeagueStandings.promotionCount(league, properties),
                            LeagueStandings.demotionCount(league, size, properties)
                    );
                })
                .toList();
    }

    /**
     * @param league        лига; null — лига текущего спикера или BRONZE для остальных
     * @param currentUserId кто смотрит, для отметки своей строки
     */
    @Transactional
    public LeagueLeaderboardResponse leagueLeaderboard(League league, Long currentUserId) {
        GamificationSeasonsRecord season = seasonService.current();
        List<Participant> participants = speakerDirectory.participants();
        League target = league != null ? league : participants.stream()
                .filter(participant -> participant.id().equals(currentUserId))
                .map(Participant::league)
                .findFirst()
                .orElse(League.BRONZE);

        Map<Long, UsersRecord> users = participants.stream()
                .collect(Collectors.toMap(Participant::id, Participant::user));
        LeagueTable table = leagueTable(target, participants, weeklyTotals(season));

        List<LeaderboardEntry> entries = table.standings().stream()
                .map(standing -> entry(standing.rank(), users.get(standing.speakerId()), target,
                        standing.points(), standing.zone(), currentUserId))
                .toList();
        return new LeagueLeaderboardResponse(
                target,
                SeasonService.toResponse(season),
                table.promotionCount(),
                table.demotionCount(),
                entries
        );
    }

    @Transactional
    public GlobalLeaderboardResponse globalLeaderboard(LeaderboardPeriod period, int limit, Long currentUserId) {
        GamificationSeasonsRecord season = period == LeaderboardPeriod.WEEK ? seasonService.current() : null;
        Map<Long, PointsTotal> totals = season != null
                ? weeklyTotals(season)
                : transactionRepository.totalsBySpeaker(null, null);

        List<Participant> participants = speakerDirectory.participants();
        Map<Long, Participant> byId = participants.stream()
                .collect(Collectors.toMap(Participant::id, Function.identity()));
        List<Contender> sorted = participants.stream()
                .map(participant -> contender(participant.id(), totals))
                .sorted(LeagueStandings.ORDER)
                .limit(limit)
                .toList();

        List<LeaderboardEntry> entries = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            Contender contender = sorted.get(i);
            Participant participant = byId.get(contender.speakerId());
            entries.add(entry(i + 1, participant.user(), participant.league(), contender.points(), null, currentUserId));
        }
        return new GlobalLeaderboardResponse(
                period,
                season == null ? null : SeasonService.toResponse(season),
                entries
        );
    }

    public Map<Long, PointsTotal> weeklyTotals(GamificationSeasonsRecord season) {
        return transactionRepository.totalsBySpeaker(season.getStartsAt(), season.getEndsAt());
    }

    public LeagueTable leagueTable(League league, List<Participant> participants, Map<Long, PointsTotal> totals) {
        List<Contender> contenders = participants.stream()
                .filter(participant -> participant.league() == league)
                .map(participant -> contender(participant.id(), totals))
                .toList();
        return new LeagueTable(
                league,
                LeagueStandings.rank(league, contenders, properties),
                LeagueStandings.promotionCount(league, properties),
                LeagueStandings.demotionCount(league, contenders.size(), properties)
        );
    }

    private static Contender contender(Long speakerId, Map<Long, PointsTotal> totals) {
        PointsTotal total = totals.getOrDefault(speakerId, PointsTotal.ZERO);
        return new Contender(speakerId, total.points(), total.lastEarnedAt());
    }

    private static LeaderboardEntry entry(int rank, UsersRecord user, League league, int points, LeagueZone zone, Long currentUserId) {
        return new LeaderboardEntry(
                rank,
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarColor(),
                league,
                points,
                zone,
                Objects.equals(user.getId(), currentUserId)
        );
    }
}
