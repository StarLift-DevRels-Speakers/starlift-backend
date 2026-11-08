package ru.shaxowskiy.javaspeakerclub.audience.fit;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.AudienceFitChecks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.AudienceFitChecksRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AudienceFitCheckRepository {

    private static final AudienceFitChecks C = AudienceFitChecks.AUDIENCE_FIT_CHECKS;

    private final DSLContext dsl;

    public record Completion(
            UUID mediaId,
            String status,
            String contentHash,
            String materialJson,
            String resultJson,
            String validationJson,
            String model,
            String promptVersion,
            String error
    ) {
    }

    public AudienceFitChecksRecord insertPending(UUID eventId, Long speakerId, UUID talkId, UUID mediaId, String trigger) {
        LocalDateTime now = LocalDateTime.now();
        return dsl.insertInto(C)
                .set(C.ID, UUID.randomUUID())
                .set(C.EVENT_ID, eventId)
                .set(C.SPEAKER_ID, speakerId)
                .set(C.SPEAKER_TALK_ID, talkId)
                .set(C.MEDIA_ID, mediaId)
                .set(C.TRIGGER_SOURCE, trigger)
                .set(C.STATUS, AudienceFitService.STATUS_PENDING)
                .set(C.CREATED_DATE, now)
                .set(C.UPDATED_DATE, now)
                .returning()
                .fetchOne();
    }

    public Optional<AudienceFitChecksRecord> findById(UUID id) {
        return dsl.selectFrom(C).where(C.ID.eq(id)).fetchOptional();
    }

    public void complete(UUID id, Completion completion) {
        dsl.update(C)
                .set(C.MEDIA_ID, completion.mediaId())
                .set(C.STATUS, completion.status())
                .set(C.CONTENT_HASH, completion.contentHash())
                .set(C.MATERIAL_JSON, completion.materialJson())
                .set(C.RESULT_JSON, completion.resultJson())
                .set(C.VALIDATION_JSON, completion.validationJson())
                .set(C.MODEL, completion.model())
                .set(C.PROMPT_VERSION, completion.promptVersion())
                .set(C.ERROR, completion.error())
                .set(C.UPDATED_DATE, LocalDateTime.now())
                .where(C.ID.eq(id))
                .execute();
    }

    /**
     * Готовая проверка того же материала под то же мероприятие, сделанная после последнего изменения профиля.
     */
    public Optional<AudienceFitChecksRecord> findReadyByContent(UUID eventId, String contentHash, LocalDateTime notBefore) {
        return dsl.selectFrom(C)
                .where(C.EVENT_ID.eq(eventId))
                .and(C.CONTENT_HASH.eq(contentHash))
                .and(C.STATUS.eq(AudienceFitService.STATUS_READY))
                .and(C.UPDATED_DATE.ge(notBefore))
                .orderBy(C.UPDATED_DATE.desc())
                .limit(1)
                .fetchOptional();
    }

    /**
     * Проверки спикера, новые первыми; фильтры по мероприятию, докладу и файлу необязательны.
     */
    public List<AudienceFitChecksRecord> findBySpeaker(Long speakerId, UUID eventId, UUID talkId, UUID mediaId, int limit) {
        Condition condition = C.SPEAKER_ID.eq(speakerId);
        if (eventId != null) {
            condition = condition.and(C.EVENT_ID.eq(eventId));
        }
        if (talkId != null) {
            condition = condition.and(C.SPEAKER_TALK_ID.eq(talkId));
        }
        if (mediaId != null) {
            condition = condition.and(C.MEDIA_ID.eq(mediaId));
        }
        return dsl.selectFrom(C).where(condition).orderBy(C.CREATED_DATE.desc()).limit(limit).fetch();
    }

    /**
     * Готовые проверки по мероприятию — для организатора, новые первыми.
     */
    public List<AudienceFitChecksRecord> findReadyByEvent(UUID eventId, int limit) {
        return dsl.selectFrom(C)
                .where(C.EVENT_ID.eq(eventId))
                .and(C.STATUS.eq(AudienceFitService.STATUS_READY))
                .orderBy(C.CREATED_DATE.desc())
                .limit(limit)
                .fetch();
    }
}
