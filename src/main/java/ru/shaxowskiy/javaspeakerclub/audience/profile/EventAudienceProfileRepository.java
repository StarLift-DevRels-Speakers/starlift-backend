package ru.shaxowskiy.javaspeakerclub.audience.profile;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.EventAudienceProfiles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventAudienceProfilesRecord;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EventAudienceProfileRepository {

    private static final EventAudienceProfiles P = EventAudienceProfiles.EVENT_AUDIENCE_PROFILES;

    private final DSLContext dsl;

    public record SaveProfile(
            UUID eventId,
            String status,
            String profileJson,
            String inputsJson,
            String inputsHash,
            String validationJson,
            boolean manual,
            String model,
            String promptVersion,
            String error
    ) {
    }

    public Optional<EventAudienceProfilesRecord> findByEventId(UUID eventId) {
        return dsl.selectFrom(P).where(P.EVENT_ID.eq(eventId)).fetchOptional();
    }

    /**
     * Один профиль на мероприятие: новая версия заменяет прежнюю.
     */
    public EventAudienceProfilesRecord save(SaveProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(P)
                .set(P.EVENT_ID, profile.eventId())
                .set(P.STATUS, profile.status())
                .set(P.PROFILE_JSON, profile.profileJson())
                .set(P.INPUTS_JSON, profile.inputsJson())
                .set(P.INPUTS_HASH, profile.inputsHash())
                .set(P.VALIDATION_JSON, profile.validationJson())
                .set(P.MANUAL, profile.manual())
                .set(P.MODEL, profile.model())
                .set(P.PROMPT_VERSION, profile.promptVersion())
                .set(P.ERROR, profile.error())
                .set(P.UPDATED_DATE, now)
                .onConflict(P.EVENT_ID)
                .doUpdate()
                .set(P.STATUS, profile.status())
                .set(P.PROFILE_JSON, profile.profileJson())
                .set(P.INPUTS_JSON, profile.inputsJson())
                .set(P.INPUTS_HASH, profile.inputsHash())
                .set(P.VALIDATION_JSON, profile.validationJson())
                .set(P.MANUAL, profile.manual())
                .set(P.MODEL, profile.model())
                .set(P.PROMPT_VERSION, profile.promptVersion())
                .set(P.ERROR, profile.error())
                .set(P.UPDATED_DATE, now)
                .execute();
        return findByEventId(profile.eventId()).orElseThrow();
    }
}
