package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Lectures;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.LecturesRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LectureRepository {

    private final DSLContext dsl;

    public List<LecturesRecord> findByTalkId(UUID talkId) {
        return dsl.selectFrom(Lectures.LECTURES)
                .where(Lectures.LECTURES.TALK_ID.eq(talkId))
                .orderBy(Lectures.LECTURES.CREATED_DATE.desc())
                .fetch();
    }

    public Optional<LecturesRecord> findById(UUID id) {
        return dsl.selectFrom(Lectures.LECTURES)
                .where(Lectures.LECTURES.ID.eq(id))
                .fetchOptional();
    }

    public LecturesRecord create(String title, UUID talkId, Long speakerId) {
        return dsl.insertInto(Lectures.LECTURES)
                .set(Lectures.LECTURES.ID, UUID.randomUUID())
                .set(Lectures.LECTURES.TITLE, title)
                .set(Lectures.LECTURES.TALK_ID, talkId)
                .set(Lectures.LECTURES.SPEAKER_ID, speakerId)
                .returning()
                .fetchOne();
    }

    public Optional<LecturesRecord> update(UUID id, String title) {
        int updated = dsl.update(Lectures.LECTURES)
                .set(Lectures.LECTURES.TITLE, title)
                .where(Lectures.LECTURES.ID.eq(id))
                .execute();

        return updated > 0 ? findById(id) : Optional.empty();
    }

    public List<LecturesRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(Lectures.LECTURES)
                .where(Lectures.LECTURES.SPEAKER_ID.eq(speakerId))
                .orderBy(Lectures.LECTURES.CREATED_DATE.desc())
                .fetch();
    }

    public boolean deleteById(UUID id) {
        return dsl.deleteFrom(Lectures.LECTURES)
                .where(Lectures.LECTURES.ID.eq(id))
                .execute() > 0;
    }

    public Optional<LecturesRecord> updateMediaS3Key(UUID id, String mediaS3Key) {
        int updated = dsl.update(Lectures.LECTURES)
                .set(Lectures.LECTURES.MEDIA_S3_KEY, mediaS3Key)
                .where(Lectures.LECTURES.ID.eq(id))
                .execute();

        return updated > 0 ? findById(id) : Optional.empty();
    }
}
