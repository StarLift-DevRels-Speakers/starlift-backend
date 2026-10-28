package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerTalks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerTalksRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SpeakerTalkRepository {

    private final DSLContext dsl;

    public List<SpeakerTalksRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(SpeakerTalks.SPEAKER_TALKS)
                .where(SpeakerTalks.SPEAKER_TALKS.SPEAKER_ID.eq(speakerId))
                .orderBy(SpeakerTalks.SPEAKER_TALKS.CREATED_DATE.desc())
                .fetch();
    }

    public Optional<SpeakerTalksRecord> findById(UUID id) {
        return dsl.selectFrom(SpeakerTalks.SPEAKER_TALKS)
                .where(SpeakerTalks.SPEAKER_TALKS.ID.eq(id))
                .fetchOptional();
    }

    public SpeakerTalksRecord create(Long speakerId, String name, String topic, String description) {
        return dsl.insertInto(SpeakerTalks.SPEAKER_TALKS)
                .set(SpeakerTalks.SPEAKER_TALKS.ID, UUID.randomUUID())
                .set(SpeakerTalks.SPEAKER_TALKS.SPEAKER_ID, speakerId)
                .set(SpeakerTalks.SPEAKER_TALKS.NAME, name)
                .set(SpeakerTalks.SPEAKER_TALKS.TOPIC, topic)
                .set(SpeakerTalks.SPEAKER_TALKS.DESCRIPTION, description)
                .set(SpeakerTalks.SPEAKER_TALKS.CREATED_DATE, LocalDateTime.now())
                .returning()
                .fetchOne();
    }

    public Optional<SpeakerTalksRecord> update(UUID id, String name, String topic, String description) {
        int updated = dsl.update(SpeakerTalks.SPEAKER_TALKS)
                .set(SpeakerTalks.SPEAKER_TALKS.NAME, name)
                .set(SpeakerTalks.SPEAKER_TALKS.TOPIC, topic)
                .set(SpeakerTalks.SPEAKER_TALKS.DESCRIPTION, description)
                .where(SpeakerTalks.SPEAKER_TALKS.ID.eq(id))
                .execute();
        return updated > 0 ? findById(id) : Optional.empty();
    }
}
