package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.EventRequests;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventRequestsRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EventRequestRepository {

    private static final EventRequests EVENT_REQUESTS = EventRequests.EVENT_REQUESTS;

    private final DSLContext dsl;

    public record EventRequestInput(
            Long organizerId,
            String title,
            String description,
            String stateJson,
            String status,
            String analysisModel,
            String analysisPromptVersion
    ) {
    }

    public EventRequestsRecord insert(EventRequestInput input) {
        LocalDateTime now = LocalDateTime.now();
        return dsl.insertInto(EVENT_REQUESTS)
                .set(EVENT_REQUESTS.ID, UUID.randomUUID())
                .set(EVENT_REQUESTS.ORGANIZER_ID, input.organizerId())
                .set(EVENT_REQUESTS.TITLE, input.title())
                .set(EVENT_REQUESTS.DESCRIPTION, input.description())
                .set(EVENT_REQUESTS.STATE_JSON, input.stateJson())
                .set(EVENT_REQUESTS.STATUS, input.status())
                .set(EVENT_REQUESTS.ANALYSIS_MODEL, input.analysisModel())
                .set(EVENT_REQUESTS.ANALYSIS_PROMPT_VERSION, input.analysisPromptVersion())
                .set(EVENT_REQUESTS.CREATED_DATE, now)
                .set(EVENT_REQUESTS.UPDATED_DATE, now)
                .returning()
                .fetchOne();
    }

    public EventRequestsRecord update(UUID id, EventRequestInput input) {
        dsl.update(EVENT_REQUESTS)
                .set(EVENT_REQUESTS.TITLE, input.title())
                .set(EVENT_REQUESTS.DESCRIPTION, input.description())
                .set(EVENT_REQUESTS.STATE_JSON, input.stateJson())
                .set(EVENT_REQUESTS.STATUS, input.status())
                .set(EVENT_REQUESTS.ANALYSIS_MODEL, input.analysisModel())
                .set(EVENT_REQUESTS.ANALYSIS_PROMPT_VERSION, input.analysisPromptVersion())
                .set(EVENT_REQUESTS.UPDATED_DATE, LocalDateTime.now())
                .where(EVENT_REQUESTS.ID.eq(id))
                .execute();
        return findById(id).orElseThrow();
    }

    public Optional<EventRequestsRecord> findById(UUID id) {
        return dsl.selectFrom(EVENT_REQUESTS)
                .where(EVENT_REQUESTS.ID.eq(id))
                .fetchOptional();
    }

    public List<EventRequestsRecord> findByOrganizerId(Long organizerId) {
        return dsl.selectFrom(EVENT_REQUESTS)
                .where(EVENT_REQUESTS.ORGANIZER_ID.eq(organizerId))
                .orderBy(EVENT_REQUESTS.UPDATED_DATE.desc())
                .fetch();
    }

    public List<EventRequestsRecord> findAll() {
        return dsl.selectFrom(EVENT_REQUESTS)
                .orderBy(EVENT_REQUESTS.UPDATED_DATE.desc())
                .fetch();
    }
}
