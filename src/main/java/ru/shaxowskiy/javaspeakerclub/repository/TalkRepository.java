package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Talks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TalkRepository {

    private final DSLContext dsl;

    public List<TalksRecord> findAll() {
        return dsl.selectFrom(Talks.TALKS)
                .orderBy(Talks.TALKS.TALK_DATE.desc())
                .fetch();
    }

    public Optional<TalksRecord> findById(UUID id) {
        return dsl.selectFrom(Talks.TALKS)
                .where(Talks.TALKS.ID.eq(id))
                .fetchOptional();
    }

    public List<TalksRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(Talks.TALKS)
                .where(Talks.TALKS.SPEAKER_ID.eq(speakerId))
                .orderBy(Talks.TALKS.TALK_DATE.desc())
                .fetch();
    }

    public List<TalksRecord> findBySpeakerIds(List<Long> speakerIds) {
        if (speakerIds == null || speakerIds.isEmpty()) {
            return List.of();
        }

        return dsl.selectFrom(Talks.TALKS)
                .where(Talks.TALKS.SPEAKER_ID.in(speakerIds))
                .orderBy(Talks.TALKS.SPEAKER_ID.asc(), Talks.TALKS.TALK_DATE.desc())
                .fetch();
    }

    public List<TalksRecord> findByConferenceType(String conferenceType) {
        return dsl.selectFrom(Talks.TALKS)
                .where(Talks.TALKS.CONFERENCE_TYPE.eq(conferenceType))
                .orderBy(Talks.TALKS.TALK_DATE.desc())
                .fetch();
    }

    public TalksRecord create(Long speakerId, String topic, LocalDateTime talkDate,
                              String conferenceType, String format,
                              String activityName, LocalDateTime activityDate) {
        return dsl.insertInto(Talks.TALKS)
                .set(Talks.TALKS.ID, UUID.randomUUID())
                .set(Talks.TALKS.SPEAKER_ID, speakerId)
                .set(Talks.TALKS.TOPIC, topic)
                .set(Talks.TALKS.TALK_DATE, talkDate)
                .set(Talks.TALKS.CONFERENCE_TYPE, conferenceType)
                .set(Talks.TALKS.FORMAT, format)
                .set(Talks.TALKS.ACTIVITY_NAME, activityName)
                .set(Talks.TALKS.ACTIVITY_DATE, activityDate)
                .returning()
                .fetchOne();
    }

    public Optional<TalksRecord> update(UUID id, String topic, LocalDateTime talkDate,
                                        String conferenceType, String format,
                                        String activityName, LocalDateTime activityDate) {
        int updated = dsl.update(Talks.TALKS)
                .set(Talks.TALKS.TOPIC, topic)
                .set(Talks.TALKS.TALK_DATE, talkDate)
                .set(Talks.TALKS.CONFERENCE_TYPE, conferenceType)
                .set(Talks.TALKS.FORMAT, format)
                .set(Talks.TALKS.ACTIVITY_NAME, activityName)
                .set(Talks.TALKS.ACTIVITY_DATE, activityDate)
                .where(Talks.TALKS.ID.eq(id))
                .execute();

        return updated > 0 ? findById(id) : Optional.empty();
    }

    public boolean deleteById(UUID id) {
        return dsl.deleteFrom(Talks.TALKS)
                .where(Talks.TALKS.ID.eq(id))
                .execute() > 0;
    }
}
