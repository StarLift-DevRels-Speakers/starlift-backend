package ru.shaxowskiy.javaspeakerclub.gamification.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.gamification.model.BadgeCode;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerBadgesRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerBadges.SPEAKER_BADGES;

@Repository
@RequiredArgsConstructor
public class BadgeRepository {

    private final DSLContext dsl;

    /**
     * Выдаёт значок, если его ещё нет. Разовые значки уникальны на спикера, сезонные — на спикера и сезон.
     */
    public Optional<SpeakerBadgesRecord> awardIfAbsent(Long speakerId, BadgeCode code, Long seasonId, LocalDateTime now) {
        UUID id = UUID.randomUUID();
        int inserted = dsl.insertInto(SPEAKER_BADGES)
                .set(SPEAKER_BADGES.ID, id)
                .set(SPEAKER_BADGES.SPEAKER_ID, speakerId)
                .set(SPEAKER_BADGES.CODE, code.name())
                .set(SPEAKER_BADGES.SCOPE_KEY, code.repeatable() && seasonId != null ? seasonId.toString() : "")
                .set(SPEAKER_BADGES.SEASON_ID, seasonId)
                .set(SPEAKER_BADGES.AWARDED_DATE, now)
                .onConflict(SPEAKER_BADGES.SPEAKER_ID, SPEAKER_BADGES.CODE, SPEAKER_BADGES.SCOPE_KEY)
                .doNothing()
                .execute();
        if (inserted == 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(SPEAKER_BADGES)
                .where(SPEAKER_BADGES.ID.eq(id))
                .fetchOptional();
    }

    public List<SpeakerBadgesRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(SPEAKER_BADGES)
                .where(SPEAKER_BADGES.SPEAKER_ID.eq(speakerId))
                .orderBy(SPEAKER_BADGES.AWARDED_DATE.asc())
                .fetch();
    }
}
