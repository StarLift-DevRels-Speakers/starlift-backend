package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.ProducerRuns;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerRunsRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Журнал запусков AI-продюсера (п. 26 ТЗ): кто запускал, по какому объекту, входные данные, кого рассматривали,
 * что вернули, версия модели и промпта. Ход рассуждений модели не хранится.
 */
@Repository
@RequiredArgsConstructor
public class ProducerRunRepository {

    private static final ProducerRuns PRODUCER_RUNS = ProducerRuns.PRODUCER_RUNS;

    private final DSLContext dsl;

    public record NewRun(
            String scenario,
            Long userId,
            String objectId,
            String inputJson,
            String consideredJson,
            String resultJson,
            String model,
            String promptVersion
    ) {
    }

    public ProducerRunsRecord insert(NewRun run) {
        return dsl.insertInto(PRODUCER_RUNS)
                .set(PRODUCER_RUNS.ID, UUID.randomUUID())
                .set(PRODUCER_RUNS.SCENARIO, run.scenario())
                .set(PRODUCER_RUNS.USER_ID, run.userId())
                .set(PRODUCER_RUNS.OBJECT_ID, run.objectId())
                .set(PRODUCER_RUNS.INPUT_JSON, run.inputJson())
                .set(PRODUCER_RUNS.CONSIDERED_JSON, run.consideredJson())
                .set(PRODUCER_RUNS.RESULT_JSON, run.resultJson())
                .set(PRODUCER_RUNS.MODEL, run.model())
                .set(PRODUCER_RUNS.PROMPT_VERSION, run.promptVersion())
                .set(PRODUCER_RUNS.CREATED_DATE, LocalDateTime.now())
                .returning()
                .fetchOne();
    }

    /** Результат дописывается после вставки: в ответе пользователю уже есть id запуска. */
    public void updateResult(UUID id, String resultJson) {
        dsl.update(PRODUCER_RUNS)
                .set(PRODUCER_RUNS.RESULT_JSON, resultJson)
                .where(PRODUCER_RUNS.ID.eq(id))
                .execute();
    }

    public Optional<ProducerRunsRecord> findLatest(String scenario, String objectId) {
        return dsl.selectFrom(PRODUCER_RUNS)
                .where(PRODUCER_RUNS.SCENARIO.eq(scenario))
                .and(PRODUCER_RUNS.OBJECT_ID.eq(objectId))
                .orderBy(PRODUCER_RUNS.CREATED_DATE.desc())
                .limit(1)
                .fetchOptional();
    }

    public List<ProducerRunsRecord> findByScenario(String scenario) {
        return dsl.selectFrom(PRODUCER_RUNS)
                .where(PRODUCER_RUNS.SCENARIO.eq(scenario))
                .orderBy(PRODUCER_RUNS.CREATED_DATE.asc())
                .fetch();
    }

    public List<ProducerRunsRecord> findRecent(int limit) {
        return dsl.selectFrom(PRODUCER_RUNS)
                .orderBy(PRODUCER_RUNS.CREATED_DATE.desc())
                .limit(limit)
                .fetch();
    }
}
