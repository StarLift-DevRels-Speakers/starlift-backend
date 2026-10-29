package ru.shaxowskiy.javaspeakerclub.gamification.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerGamificationProfilesRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES;

/**
 * Профиль спикера в соревновании. Создаётся лениво: спикер без строки считается участником BRONZE с нулевой серией.
 */
@Repository
@RequiredArgsConstructor
public class GamificationProfileRepository {

    private final DSLContext dsl;

    public Optional<SpeakerGamificationProfilesRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(SPEAKER_GAMIFICATION_PROFILES)
                .where(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID.eq(speakerId))
                .fetchOptional();
    }

    /**
     * Профиль под блокировкой строки — для изменения серии без гонок при параллельных отметках.
     */
    public SpeakerGamificationProfilesRecord lockOrCreate(Long speakerId, LocalDateTime now) {
        dsl.insertInto(SPEAKER_GAMIFICATION_PROFILES)
                .set(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID, speakerId)
                .set(SPEAKER_GAMIFICATION_PROFILES.LEAGUE, League.BRONZE.name())
                .set(SPEAKER_GAMIFICATION_PROFILES.CREATED_DATE, now)
                .set(SPEAKER_GAMIFICATION_PROFILES.LAST_MODIFIED_DATE, now)
                .onConflict(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID)
                .doNothing()
                .execute();
        return dsl.selectFrom(SPEAKER_GAMIFICATION_PROFILES)
                .where(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID.eq(speakerId))
                .forUpdate()
                .fetchOne();
    }

    public Map<Long, League> findLeagues(Collection<Long> speakerIds) {
        Map<Long, League> leagues = new HashMap<>();
        if (speakerIds.isEmpty()) {
            return leagues;
        }
        dsl.select(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID, SPEAKER_GAMIFICATION_PROFILES.LEAGUE)
                .from(SPEAKER_GAMIFICATION_PROFILES)
                .where(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID.in(speakerIds))
                .fetch()
                .forEach(row -> leagues.put(row.value1(), League.valueOf(row.value2())));
        return leagues;
    }

    public void updateStreak(Long speakerId, int currentStreak, int longestStreak, LocalDate lastActiveDate, LocalDateTime now) {
        dsl.update(SPEAKER_GAMIFICATION_PROFILES)
                .set(SPEAKER_GAMIFICATION_PROFILES.CURRENT_STREAK, currentStreak)
                .set(SPEAKER_GAMIFICATION_PROFILES.LONGEST_STREAK, longestStreak)
                .set(SPEAKER_GAMIFICATION_PROFILES.LAST_ACTIVE_DATE, lastActiveDate)
                .set(SPEAKER_GAMIFICATION_PROFILES.LAST_MODIFIED_DATE, now)
                .where(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID.eq(speakerId))
                .execute();
    }

    public void upsertLeague(Long speakerId, League league, LocalDateTime now) {
        dsl.insertInto(SPEAKER_GAMIFICATION_PROFILES)
                .set(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID, speakerId)
                .set(SPEAKER_GAMIFICATION_PROFILES.LEAGUE, league.name())
                .set(SPEAKER_GAMIFICATION_PROFILES.CREATED_DATE, now)
                .set(SPEAKER_GAMIFICATION_PROFILES.LAST_MODIFIED_DATE, now)
                .onConflict(SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID)
                .doUpdate()
                .set(SPEAKER_GAMIFICATION_PROFILES.LEAGUE, league.name())
                .set(SPEAKER_GAMIFICATION_PROFILES.LAST_MODIFIED_DATE, now)
                .execute();
    }
}
