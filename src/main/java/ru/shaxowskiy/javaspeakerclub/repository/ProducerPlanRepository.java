package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.ProducerPlans;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerPlansRecord;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProducerPlanRepository {

    private final DSLContext dsl;

    public record NewPlan(
            Long speakerId,
            String status,
            String planJson,
            String snapshotJson,
            String validationJson,
            String model,
            String promptVersion,
            String error
    ) {
    }

    public ProducerPlansRecord insert(NewPlan plan) {
        return dsl.insertInto(ProducerPlans.PRODUCER_PLANS)
                .set(ProducerPlans.PRODUCER_PLANS.ID, UUID.randomUUID())
                .set(ProducerPlans.PRODUCER_PLANS.SPEAKER_ID, plan.speakerId())
                .set(ProducerPlans.PRODUCER_PLANS.STATUS, plan.status())
                .set(ProducerPlans.PRODUCER_PLANS.PLAN_JSON, plan.planJson())
                .set(ProducerPlans.PRODUCER_PLANS.SNAPSHOT_JSON, plan.snapshotJson())
                .set(ProducerPlans.PRODUCER_PLANS.VALIDATION_JSON, plan.validationJson())
                .set(ProducerPlans.PRODUCER_PLANS.MODEL, plan.model())
                .set(ProducerPlans.PRODUCER_PLANS.PROMPT_VERSION, plan.promptVersion())
                .set(ProducerPlans.PRODUCER_PLANS.ERROR, plan.error())
                .set(ProducerPlans.PRODUCER_PLANS.CREATED_DATE, LocalDateTime.now())
                .returning()
                .fetchOne();
    }

    public Optional<ProducerPlansRecord> findLatestBySpeakerId(Long speakerId) {
        return dsl.selectFrom(ProducerPlans.PRODUCER_PLANS)
                .where(ProducerPlans.PRODUCER_PLANS.SPEAKER_ID.eq(speakerId))
                .orderBy(ProducerPlans.PRODUCER_PLANS.CREATED_DATE.desc())
                .limit(1)
                .fetchOptional();
    }

    public Map<Long, ProducerPlansRecord> findLatestBySpeakerIds(List<Long> speakerIds) {
        if (speakerIds == null || speakerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProducerPlansRecord> latest = new LinkedHashMap<>();
        dsl.selectFrom(ProducerPlans.PRODUCER_PLANS)
                .where(ProducerPlans.PRODUCER_PLANS.SPEAKER_ID.in(speakerIds))
                .orderBy(ProducerPlans.PRODUCER_PLANS.CREATED_DATE.desc())
                .fetch()
                .forEach(record -> latest.putIfAbsent(record.getSpeakerId(), record));
        return latest;
    }
}
