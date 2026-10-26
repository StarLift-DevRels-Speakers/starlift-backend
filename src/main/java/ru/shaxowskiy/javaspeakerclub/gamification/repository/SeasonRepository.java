package ru.shaxowskiy.javaspeakerclub.gamification.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonStatus;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationSeasonsRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static ru.shaxowskiy.javaspeakerclub.jooq.tables.GamificationSeasons.GAMIFICATION_SEASONS;

@Repository
@RequiredArgsConstructor
public class SeasonRepository {

    private final DSLContext dsl;

    public Optional<GamificationSeasonsRecord> findById(Long id) {
        return dsl.selectFrom(GAMIFICATION_SEASONS)
                .where(GAMIFICATION_SEASONS.ID.eq(id))
                .fetchOptional();
    }

    public Optional<GamificationSeasonsRecord> lockById(Long id) {
        return dsl.selectFrom(GAMIFICATION_SEASONS)
                .where(GAMIFICATION_SEASONS.ID.eq(id))
                .forUpdate()
                .fetchOptional();
    }

    public Optional<GamificationSeasonsRecord> findCovering(LocalDateTime moment) {
        return dsl.selectFrom(GAMIFICATION_SEASONS)
                .where(GAMIFICATION_SEASONS.STARTS_AT.le(moment))
                .and(GAMIFICATION_SEASONS.ENDS_AT.gt(moment))
                .orderBy(GAMIFICATION_SEASONS.STARTS_AT.desc())
                .limit(1)
                .fetchOptional();
    }

    public Optional<GamificationSeasonsRecord> findLatest() {
        return dsl.selectFrom(GAMIFICATION_SEASONS)
                .orderBy(GAMIFICATION_SEASONS.STARTS_AT.desc())
                .limit(1)
                .fetchOptional();
    }

    public Optional<GamificationSeasonsRecord> findByStartsAt(LocalDateTime startsAt) {
        return dsl.selectFrom(GAMIFICATION_SEASONS)
                .where(GAMIFICATION_SEASONS.STARTS_AT.eq(startsAt))
                .fetchOptional();
    }

    public List<GamificationSeasonsRecord> findRecent(int limit) {
        return dsl.selectFrom(GAMIFICATION_SEASONS)
                .orderBy(GAMIFICATION_SEASONS.STARTS_AT.desc())
                .limit(limit)
                .fetch();
    }

    public List<GamificationSeasonsRecord> findActiveEndedBy(LocalDateTime moment) {
        return dsl.selectFrom(GAMIFICATION_SEASONS)
                .where(GAMIFICATION_SEASONS.STATUS.eq(SeasonStatus.ACTIVE.name()))
                .and(GAMIFICATION_SEASONS.ENDS_AT.le(moment))
                .orderBy(GAMIFICATION_SEASONS.STARTS_AT.asc())
                .fetch();
    }

    /**
     * Создаёт сезон, если сезона с таким началом ещё нет (параллельные запросы в начале недели).
     */
    public void insertIfAbsent(LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        dsl.insertInto(GAMIFICATION_SEASONS)
                .set(GAMIFICATION_SEASONS.STARTS_AT, startsAt)
                .set(GAMIFICATION_SEASONS.ENDS_AT, endsAt)
                .set(GAMIFICATION_SEASONS.STATUS, SeasonStatus.ACTIVE.name())
                .set(GAMIFICATION_SEASONS.CREATED_DATE, now)
                .onConflict(GAMIFICATION_SEASONS.STARTS_AT)
                .doNothing()
                .execute();
    }

    public void shortenTo(Long id, LocalDateTime endsAt) {
        dsl.update(GAMIFICATION_SEASONS)
                .set(GAMIFICATION_SEASONS.ENDS_AT, endsAt)
                .where(GAMIFICATION_SEASONS.ID.eq(id))
                .execute();
    }

    public void markClosed(Long id, LocalDateTime closedAt) {
        dsl.update(GAMIFICATION_SEASONS)
                .set(GAMIFICATION_SEASONS.STATUS, SeasonStatus.CLOSED.name())
                .set(GAMIFICATION_SEASONS.CLOSED_AT, closedAt)
                .where(GAMIFICATION_SEASONS.ID.eq(id))
                .execute();
    }
}
