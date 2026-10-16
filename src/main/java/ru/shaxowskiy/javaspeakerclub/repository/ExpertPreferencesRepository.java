package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.ExpertPreferences;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ExpertPreferencesRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ExpertPreferencesRepository {

    private static final ExpertPreferences EXPERT_PREFERENCES = ExpertPreferences.EXPERT_PREFERENCES;

    private final DSLContext dsl;

    /** Списки хранятся строкой через запятую: языки, форматы и интересы — короткие и без запятых внутри. */
    public record PreferencesInput(
            String languages,
            String formats,
            Boolean onlineOk,
            Boolean offlineOk,
            String interests,
            String limitations
    ) {
    }

    public Optional<ExpertPreferencesRecord> findByUserId(Long userId) {
        return dsl.selectFrom(EXPERT_PREFERENCES)
                .where(EXPERT_PREFERENCES.USER_ID.eq(userId))
                .fetchOptional();
    }

    public Map<Long, ExpertPreferencesRecord> findByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return dsl.selectFrom(EXPERT_PREFERENCES)
                .where(EXPERT_PREFERENCES.USER_ID.in(userIds))
                .fetchMap(EXPERT_PREFERENCES.USER_ID);
    }

    public ExpertPreferencesRecord upsert(Long userId, PreferencesInput input) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(EXPERT_PREFERENCES)
                .set(EXPERT_PREFERENCES.USER_ID, userId)
                .set(EXPERT_PREFERENCES.LANGUAGES, input.languages())
                .set(EXPERT_PREFERENCES.FORMATS, input.formats())
                .set(EXPERT_PREFERENCES.ONLINE_OK, input.onlineOk())
                .set(EXPERT_PREFERENCES.OFFLINE_OK, input.offlineOk())
                .set(EXPERT_PREFERENCES.INTERESTS, input.interests())
                .set(EXPERT_PREFERENCES.LIMITATIONS, input.limitations())
                .set(EXPERT_PREFERENCES.UPDATED_DATE, now)
                .onConflict(EXPERT_PREFERENCES.USER_ID)
                .doUpdate()
                .set(EXPERT_PREFERENCES.LANGUAGES, input.languages())
                .set(EXPERT_PREFERENCES.FORMATS, input.formats())
                .set(EXPERT_PREFERENCES.ONLINE_OK, input.onlineOk())
                .set(EXPERT_PREFERENCES.OFFLINE_OK, input.offlineOk())
                .set(EXPERT_PREFERENCES.INTERESTS, input.interests())
                .set(EXPERT_PREFERENCES.LIMITATIONS, input.limitations())
                .set(EXPERT_PREFERENCES.UPDATED_DATE, now)
                .execute();
        return findByUserId(userId).orElseThrow();
    }
}
