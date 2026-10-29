package ru.shaxowskiy.javaspeakerclub.gamification.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.gamification.model.PointReason;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationPointTransactionsRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ru.shaxowskiy.javaspeakerclub.jooq.tables.GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS;

@Repository
@RequiredArgsConstructor
public class PointTransactionRepository {

    private static final Field<BigDecimal> POINTS_SUM = DSL.sum(GAMIFICATION_POINT_TRANSACTIONS.POINTS).as("points_sum");
    private static final Field<LocalDateTime> LAST_EARNED = DSL.max(GAMIFICATION_POINT_TRANSACTIONS.CREATED_DATE).as("last_earned");

    private final DSLContext dsl;

    public record NewTransaction(
            Long speakerId,
            int points,
            PointReason reason,
            String sourceId,
            String comment,
            Long awardedBy,
            LocalDateTime createdDate
    ) {
    }

    /**
     * Баллы спикера за период; lastEarnedAt нужен для разрешения ничьих (выше тот, кто набрал раньше).
     */
    public record PointsTotal(int points, LocalDateTime lastEarnedAt) {
        public static final PointsTotal ZERO = new PointsTotal(0, null);
    }

    /**
     * Вставляет начисление. Для начислений с sourceId повтор (тот же спикер, причина и источник) игнорируется.
     */
    public Optional<GamificationPointTransactionsRecord> insertIfAbsent(NewTransaction tx) {
        UUID id = UUID.randomUUID();
        int inserted = dsl.insertInto(GAMIFICATION_POINT_TRANSACTIONS)
                .set(GAMIFICATION_POINT_TRANSACTIONS.ID, id)
                .set(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID, tx.speakerId())
                .set(GAMIFICATION_POINT_TRANSACTIONS.POINTS, tx.points())
                .set(GAMIFICATION_POINT_TRANSACTIONS.REASON, tx.reason().name())
                .set(GAMIFICATION_POINT_TRANSACTIONS.SOURCE_ID, tx.sourceId())
                .set(GAMIFICATION_POINT_TRANSACTIONS.COMMENT, tx.comment())
                .set(GAMIFICATION_POINT_TRANSACTIONS.AWARDED_BY, tx.awardedBy())
                .set(GAMIFICATION_POINT_TRANSACTIONS.CREATED_DATE, tx.createdDate())
                .onConflict(
                        GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID,
                        GAMIFICATION_POINT_TRANSACTIONS.REASON,
                        GAMIFICATION_POINT_TRANSACTIONS.SOURCE_ID
                )
                .doNothing()
                .execute();
        if (inserted == 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(GAMIFICATION_POINT_TRANSACTIONS)
                .where(GAMIFICATION_POINT_TRANSACTIONS.ID.eq(id))
                .fetchOptional();
    }

    public Optional<GamificationPointTransactionsRecord> findBySource(Long speakerId, PointReason reason, String sourceId) {
        return dsl.selectFrom(GAMIFICATION_POINT_TRANSACTIONS)
                .where(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID.eq(speakerId))
                .and(GAMIFICATION_POINT_TRANSACTIONS.REASON.eq(reason.name()))
                .and(GAMIFICATION_POINT_TRANSACTIONS.SOURCE_ID.eq(sourceId))
                .fetchOptional();
    }

    public List<GamificationPointTransactionsRecord> findBySpeakerId(Long speakerId, int limit) {
        return dsl.selectFrom(GAMIFICATION_POINT_TRANSACTIONS)
                .where(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID.eq(speakerId))
                .orderBy(GAMIFICATION_POINT_TRANSACTIONS.CREATED_DATE.desc(), GAMIFICATION_POINT_TRANSACTIONS.ID.desc())
                .limit(limit)
                .fetch();
    }

    public int sumBySpeakerId(Long speakerId) {
        BigDecimal sum = dsl.select(DSL.sum(GAMIFICATION_POINT_TRANSACTIONS.POINTS))
                .from(GAMIFICATION_POINT_TRANSACTIONS)
                .where(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID.eq(speakerId))
                .fetchOne(0, BigDecimal.class);
        return sum == null ? 0 : sum.intValue();
    }

    public int sumBySpeakerAndReasonsSince(Long speakerId, Collection<PointReason> reasons, LocalDateTime since) {
        BigDecimal sum = dsl.select(DSL.sum(GAMIFICATION_POINT_TRANSACTIONS.POINTS))
                .from(GAMIFICATION_POINT_TRANSACTIONS)
                .where(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID.eq(speakerId))
                .and(GAMIFICATION_POINT_TRANSACTIONS.REASON.in(reasons.stream().map(Enum::name).toList()))
                .and(GAMIFICATION_POINT_TRANSACTIONS.CREATED_DATE.ge(since))
                .fetchOne(0, BigDecimal.class);
        return sum == null ? 0 : sum.intValue();
    }

    /**
     * Суммы по спикерам за полуинтервал [from, to). Оба конца null — за всё время.
     */
    public Map<Long, PointsTotal> totalsBySpeaker(LocalDateTime from, LocalDateTime to) {
        var condition = DSL.noCondition();
        if (from != null) {
            condition = condition.and(GAMIFICATION_POINT_TRANSACTIONS.CREATED_DATE.ge(from));
        }
        if (to != null) {
            condition = condition.and(GAMIFICATION_POINT_TRANSACTIONS.CREATED_DATE.lt(to));
        }
        Map<Long, PointsTotal> totals = new HashMap<>();
        dsl.select(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID, POINTS_SUM, LAST_EARNED)
                .from(GAMIFICATION_POINT_TRANSACTIONS)
                .where(condition)
                .groupBy(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID)
                .fetch()
                .forEach(row -> totals.put(
                        row.get(GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID),
                        new PointsTotal(
                                row.get(POINTS_SUM) == null ? 0 : row.get(POINTS_SUM).intValue(),
                                row.get(LAST_EARNED)
                        )
                ));
        return totals;
    }
}
