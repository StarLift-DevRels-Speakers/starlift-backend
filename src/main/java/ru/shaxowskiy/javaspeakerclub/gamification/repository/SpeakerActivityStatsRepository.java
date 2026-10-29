package ru.shaxowskiy.javaspeakerclub.gamification.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static ru.shaxowskiy.javaspeakerclub.jooq.tables.TalkFeedback.TALK_FEEDBACK;
import static ru.shaxowskiy.javaspeakerclub.jooq.tables.Talks.TALKS;
import static ru.shaxowskiy.javaspeakerclub.jooq.tables.Users.USERS;

/**
 * Счётчики активности спикера для значков.
 */
@Repository
@RequiredArgsConstructor
public class SpeakerActivityStatsRepository {

    private final DSLContext dsl;

    public int countTalks(Long speakerId) {
        return dsl.fetchCount(TALKS, TALKS.SPEAKER_ID.eq(speakerId));
    }

    public int countFeedback(Long speakerId) {
        return dsl.fetchCount(
                dsl.select(TALK_FEEDBACK.ID)
                        .from(TALK_FEEDBACK)
                        .join(TALKS).on(TALKS.ID.eq(TALK_FEEDBACK.TALK_ID))
                        .where(TALKS.SPEAKER_ID.eq(speakerId))
        );
    }

    public int profileCompletion(Long speakerId) {
        Integer completion = dsl.select(USERS.PROFILE_COMPLETION)
                .from(USERS)
                .where(USERS.ID.eq(speakerId))
                .fetchOne(USERS.PROFILE_COMPLETION);
        return completion == null ? 0 : completion;
    }
}
