package ru.shaxowskiy.javaspeakerclub.gamification.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonOutcome;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationSeasonResultsRecord;

import java.util.List;

import static ru.shaxowskiy.javaspeakerclub.jooq.tables.GamificationSeasonResults.GAMIFICATION_SEASON_RESULTS;

@Repository
@RequiredArgsConstructor
public class SeasonResultRepository {

    private final DSLContext dsl;

    public record NewResult(Long speakerId, League league, int rank, int points, SeasonOutcome outcome, League newLeague) {
    }

    public void insertAll(Long seasonId, List<NewResult> results) {
        if (results.isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(
                GAMIFICATION_SEASON_RESULTS,
                GAMIFICATION_SEASON_RESULTS.SEASON_ID,
                GAMIFICATION_SEASON_RESULTS.SPEAKER_ID,
                GAMIFICATION_SEASON_RESULTS.LEAGUE,
                GAMIFICATION_SEASON_RESULTS.RANK,
                GAMIFICATION_SEASON_RESULTS.POINTS,
                GAMIFICATION_SEASON_RESULTS.OUTCOME,
                GAMIFICATION_SEASON_RESULTS.NEW_LEAGUE
        );
        for (NewResult result : results) {
            insert = insert.values(
                    seasonId,
                    result.speakerId(),
                    result.league().name(),
                    result.rank(),
                    result.points(),
                    result.outcome().name(),
                    result.newLeague().name()
            );
        }
        insert.execute();
    }

    public List<GamificationSeasonResultsRecord> findBySeasonId(Long seasonId, League league) {
        var query = dsl.selectFrom(GAMIFICATION_SEASON_RESULTS)
                .where(GAMIFICATION_SEASON_RESULTS.SEASON_ID.eq(seasonId));
        if (league != null) {
            query = query.and(GAMIFICATION_SEASON_RESULTS.LEAGUE.eq(league.name()));
        }
        return query.fetch();
    }
}
