package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.TalkFeedback;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TalkFeedbackRepository {

    private final DSLContext dsl;

    public List<TalkFeedbackRecord> findAllByTalkId(UUID talkId) {
        return dsl.selectFrom(TalkFeedback.TALK_FEEDBACK)
                .where(TalkFeedback.TALK_FEEDBACK.TALK_ID.eq(talkId))
                .orderBy(TalkFeedback.TALK_FEEDBACK.CREATED_DATE.desc())
                .fetch();
    }

    public Optional<TalkFeedbackRecord> findByIdAndTalkId(UUID id, UUID talkId) {
        return dsl.selectFrom(TalkFeedback.TALK_FEEDBACK)
                .where(TalkFeedback.TALK_FEEDBACK.ID.eq(id))
                .and(TalkFeedback.TALK_FEEDBACK.TALK_ID.eq(talkId))
                .fetchOptional();
    }

    public TalkFeedbackRecord create(UUID talkId, Integer rating, String positive, String negative, String improvementSuggestions) {
        return dsl.insertInto(TalkFeedback.TALK_FEEDBACK)
                .set(TalkFeedback.TALK_FEEDBACK.ID, UUID.randomUUID())
                .set(TalkFeedback.TALK_FEEDBACK.TALK_ID, talkId)
                .set(TalkFeedback.TALK_FEEDBACK.RATING, rating)
                .set(TalkFeedback.TALK_FEEDBACK.POSITIVE, positive)
                .set(TalkFeedback.TALK_FEEDBACK.NEGATIVE, negative)
                .set(TalkFeedback.TALK_FEEDBACK.IMPROVEMENT_SUGGESTIONS, improvementSuggestions)
                .returning()
                .fetchOne();
    }
}
