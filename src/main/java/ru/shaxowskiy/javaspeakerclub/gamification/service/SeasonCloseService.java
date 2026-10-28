package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.gamification.model.BadgeCode;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonCloseResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonOutcome;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonResultResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonStatus;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.model.LeagueZone;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.GamificationProfileRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.PointTransactionRepository.PointsTotal;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.SeasonRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.SeasonResultRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.SeasonResultRepository.NewResult;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeagueStandings.Standing;
import ru.shaxowskiy.javaspeakerclub.gamification.service.SpeakerDirectory.Participant;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationSeasonResultsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationSeasonsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Итоги сезона: места по лигам, повышение/понижение и сезонные значки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonCloseService {

    private final SeasonService seasonService;
    private final SeasonRepository seasonRepository;
    private final SeasonResultRepository resultRepository;
    private final GamificationProfileRepository profileRepository;
    private final LeaderboardService leaderboardService;
    private final SpeakerDirectory speakerDirectory;
    private final BadgeService badgeService;
    private final UserRepository userRepository;

    private record CloseSummary(int participants, int promoted, int demoted) {
    }

    /**
     * Закрывает все истёкшие сезоны по порядку и заводит сезон текущей недели. Вызывается шедулером.
     */
    @Transactional
    public int closeExpired() {
        LocalDateTime now = seasonService.now();
        int closed = 0;
        for (GamificationSeasonsRecord season : seasonRepository.findActiveEndedBy(now)) {
            if (closeLocked(season.getId()) != null) {
                closed++;
            }
        }
        seasonService.current();
        return closed;
    }

    /**
     * Досрочно закрывает текущий сезон; следующий начинается сразу и длится до конца недели.
     */
    @Transactional
    public SeasonCloseResponse closeCurrent() {
        closeExpired();
        LocalDateTime now = seasonService.now();
        GamificationSeasonsRecord current = seasonService.current();
        if (!now.isAfter(current.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Season has just started, nothing to close");
        }
        seasonRepository.lockById(current.getId());
        seasonRepository.shortenTo(current.getId(), now);
        CloseSummary summary = closeLocked(current.getId());

        seasonRepository.insertIfAbsent(now, SeasonService.weekStart(now).plusWeeks(1), now);
        GamificationSeasonsRecord next = seasonRepository.findByStartsAt(now)
                .orElseThrow(() -> new IllegalStateException("Next season was not created"));
        GamificationSeasonsRecord closed = seasonService.require(current.getId());
        return new SeasonCloseResponse(
                SeasonService.toResponse(closed),
                SeasonService.toResponse(next),
                summary == null ? 0 : summary.participants(),
                summary == null ? 0 : summary.promoted(),
                summary == null ? 0 : summary.demoted()
        );
    }

    @Transactional(readOnly = true)
    public List<SeasonResultResponse> results(Long seasonId, League league) {
        GamificationSeasonsRecord season = seasonService.require(seasonId);
        if (!SeasonStatus.CLOSED.name().equals(season.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Season is not closed yet: " + seasonId);
        }
        List<GamificationSeasonResultsRecord> results = resultRepository.findBySeasonId(seasonId, league);
        Map<Long, UsersRecord> users = userRepository.findByIds(
                        results.stream().map(GamificationSeasonResultsRecord::getSpeakerId).toList())
                .stream()
                .collect(Collectors.toMap(UsersRecord::getId, Function.identity()));

        return results.stream()
                .sorted(Comparator
                        .comparing((GamificationSeasonResultsRecord r) -> League.valueOf(r.getLeague())).reversed()
                        .thenComparing(GamificationSeasonResultsRecord::getRank))
                .map(result -> {
                    UsersRecord user = users.get(result.getSpeakerId());
                    return new SeasonResultResponse(
                            seasonId,
                            result.getSpeakerId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getFirstName(),
                            user == null ? null : user.getLastName(),
                            League.valueOf(result.getLeague()),
                            result.getRank(),
                            result.getPoints(),
                            SeasonOutcome.valueOf(result.getOutcome()),
                            League.valueOf(result.getNewLeague())
                    );
                })
                .toList();
    }

    /**
     * Подводит итоги сезона под блокировкой строки сезона: параллельный шедулер на другом инстансе
     * дождётся коммита и увидит статус CLOSED.
     *
     * @return null, если сезон уже закрыт
     */
    private CloseSummary closeLocked(Long seasonId) {
        GamificationSeasonsRecord season = seasonRepository.lockById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found: " + seasonId));
        if (SeasonStatus.CLOSED.name().equals(season.getStatus())) {
            return null;
        }

        LocalDateTime now = seasonService.now();
        List<Participant> participants = speakerDirectory.participants();
        Map<Long, PointsTotal> totals = leaderboardService.weeklyTotals(season);

        List<NewResult> results = new ArrayList<>(participants.size());
        int promoted = 0;
        int demoted = 0;
        for (League league : League.values()) {
            for (Standing standing : leaderboardService.leagueTable(league, participants, totals).standings()) {
                SeasonOutcome outcome = outcome(standing.zone());
                League newLeague = switch (outcome) {
                    case PROMOTED -> league.up();
                    case DEMOTED -> league.down();
                    case STAYED -> league;
                };
                results.add(new NewResult(standing.speakerId(), league, standing.rank(), standing.points(), outcome, newLeague));
                if (newLeague != league) {
                    profileRepository.upsertLeague(standing.speakerId(), newLeague, now);
                }
                if (outcome == SeasonOutcome.PROMOTED) {
                    promoted++;
                    badgeService.award(standing.speakerId(), BadgeCode.PROMOTED, seasonId);
                    badgeService.awardReachedLeague(standing.speakerId(), newLeague);
                } else if (outcome == SeasonOutcome.DEMOTED) {
                    demoted++;
                }
                if (standing.points() > 0 && standing.rank() == 1) {
                    badgeService.award(standing.speakerId(), BadgeCode.SEASON_WINNER, seasonId);
                }
                if (standing.points() > 0 && standing.rank() <= 3) {
                    badgeService.award(standing.speakerId(), BadgeCode.SEASON_PODIUM, seasonId);
                }
            }
        }

        resultRepository.insertAll(seasonId, results);
        seasonRepository.markClosed(seasonId, now);
        log.info("Gamification season {} closed: participants={}, promoted={}, demoted={}",
                seasonId, results.size(), promoted, demoted);
        return new CloseSummary(results.size(), promoted, demoted);
    }

    private static SeasonOutcome outcome(LeagueZone zone) {
        return switch (zone) {
            case PROMOTION -> SeasonOutcome.PROMOTED;
            case DEMOTION -> SeasonOutcome.DEMOTED;
            case SAFE -> SeasonOutcome.STAYED;
        };
    }
}
