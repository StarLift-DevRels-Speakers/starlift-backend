package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.ProducerInteractions;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerInteractionsRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * История взаимодействий (п. 23–25 ТЗ): факты (kind = fact) и рекомендации AI (kind = ai) хранятся раздельно,
 * чтобы интерпретация никогда не выдавалась за факт.
 */
@Repository
@RequiredArgsConstructor
public class ProducerInteractionRepository {

    private static final ProducerInteractions PRODUCER_INTERACTIONS = ProducerInteractions.PRODUCER_INTERACTIONS;

    private final DSLContext dsl;

    public record NewInteraction(
            UUID runId,
            Long expertId,
            UUID eventRequestId,
            UUID cfpId,
            Long actorId,
            String action,
            String kind,
            String topic,
            String format,
            String note,
            String confidence
    ) {
    }

    public ProducerInteractionsRecord insert(NewInteraction interaction) {
        return dsl.insertInto(PRODUCER_INTERACTIONS)
                .set(PRODUCER_INTERACTIONS.ID, UUID.randomUUID())
                .set(PRODUCER_INTERACTIONS.RUN_ID, interaction.runId())
                .set(PRODUCER_INTERACTIONS.EXPERT_ID, interaction.expertId())
                .set(PRODUCER_INTERACTIONS.EVENT_REQUEST_ID, interaction.eventRequestId())
                .set(PRODUCER_INTERACTIONS.CFP_ID, interaction.cfpId())
                .set(PRODUCER_INTERACTIONS.ACTOR_ID, interaction.actorId())
                .set(PRODUCER_INTERACTIONS.ACTION, interaction.action())
                .set(PRODUCER_INTERACTIONS.KIND, interaction.kind())
                .set(PRODUCER_INTERACTIONS.TOPIC, interaction.topic())
                .set(PRODUCER_INTERACTIONS.FORMAT, interaction.format())
                .set(PRODUCER_INTERACTIONS.NOTE, interaction.note())
                .set(PRODUCER_INTERACTIONS.CONFIDENCE, interaction.confidence())
                .set(PRODUCER_INTERACTIONS.CREATED_DATE, LocalDateTime.now())
                .returning()
                .fetchOne();
    }

    public List<ProducerInteractionsRecord> findFactsByExpertIds(List<Long> expertIds) {
        if (expertIds == null || expertIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(PRODUCER_INTERACTIONS)
                .where(PRODUCER_INTERACTIONS.EXPERT_ID.in(expertIds))
                .and(PRODUCER_INTERACTIONS.KIND.eq("fact"))
                .orderBy(PRODUCER_INTERACTIONS.CREATED_DATE.asc())
                .fetch();
    }

    /** Факты по паре «эксперт + мероприятие организатора», от старых к новым: из них складывается статус приглашения. */
    public List<ProducerInteractionsRecord> findFacts(Long expertId, UUID eventRequestId) {
        return dsl.selectFrom(PRODUCER_INTERACTIONS)
                .where(PRODUCER_INTERACTIONS.EXPERT_ID.eq(expertId))
                .and(PRODUCER_INTERACTIONS.EVENT_REQUEST_ID.eq(eventRequestId))
                .and(PRODUCER_INTERACTIONS.KIND.eq("fact"))
                .orderBy(PRODUCER_INTERACTIONS.CREATED_DATE.asc())
                .fetch();
    }

    /** Все факты эксперта по мероприятиям организаторов с указанными действиями, от старых к новым. */
    public List<ProducerInteractionsRecord> findEventFactsByExpert(Long expertId, List<String> actions) {
        return dsl.selectFrom(PRODUCER_INTERACTIONS)
                .where(PRODUCER_INTERACTIONS.EXPERT_ID.eq(expertId))
                .and(PRODUCER_INTERACTIONS.EVENT_REQUEST_ID.isNotNull())
                .and(PRODUCER_INTERACTIONS.KIND.eq("fact"))
                .and(PRODUCER_INTERACTIONS.ACTION.in(actions))
                .orderBy(PRODUCER_INTERACTIONS.CREATED_DATE.asc())
                .fetch();
    }

    public boolean existsFact(Long expertId, Long actorId, String action) {
        return dsl.fetchExists(dsl.selectFrom(PRODUCER_INTERACTIONS)
                .where(PRODUCER_INTERACTIONS.EXPERT_ID.eq(expertId))
                .and(PRODUCER_INTERACTIONS.ACTOR_ID.eq(actorId))
                .and(PRODUCER_INTERACTIONS.ACTION.eq(action))
                .and(PRODUCER_INTERACTIONS.KIND.eq("fact")));
    }

    /** Строка для метрик: без текста заметок, только то, что нужно для воронок и долей. */
    public record MetricRow(String action, String kind, Long expertId, UUID eventRequestId, UUID cfpId, String confidence) {
    }

    public List<MetricRow> findForMetrics() {
        return dsl.select(PRODUCER_INTERACTIONS.ACTION, PRODUCER_INTERACTIONS.KIND, PRODUCER_INTERACTIONS.EXPERT_ID,
                        PRODUCER_INTERACTIONS.EVENT_REQUEST_ID, PRODUCER_INTERACTIONS.CFP_ID, PRODUCER_INTERACTIONS.CONFIDENCE)
                .from(PRODUCER_INTERACTIONS)
                .fetch(row -> new MetricRow(row.value1(), row.value2(), row.value3(), row.value4(), row.value5(), row.value6()));
    }

    /** Фильтры необязательны; null — без ограничения. */
    public List<ProducerInteractionsRecord> find(Long expertId, UUID eventRequestId, UUID cfpId, String kind, int limit) {
        Condition condition = DSL.noCondition();
        if (expertId != null) {
            condition = condition.and(PRODUCER_INTERACTIONS.EXPERT_ID.eq(expertId));
        }
        if (eventRequestId != null) {
            condition = condition.and(PRODUCER_INTERACTIONS.EVENT_REQUEST_ID.eq(eventRequestId));
        }
        if (cfpId != null) {
            condition = condition.and(PRODUCER_INTERACTIONS.CFP_ID.eq(cfpId));
        }
        if (kind != null) {
            condition = condition.and(PRODUCER_INTERACTIONS.KIND.eq(kind));
        }
        return dsl.selectFrom(PRODUCER_INTERACTIONS)
                .where(condition)
                .orderBy(PRODUCER_INTERACTIONS.CREATED_DATE.desc())
                .limit(limit)
                .fetch();
    }
}
