package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.ParsingEvents;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ParsingEventsRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ParsingEventRepository {

    private final DSLContext dsl;

    public ParsingEventsRecord insert(
            UUID id,
            String conference,
            String speakerName,
            String company,
            String sourceUrl,
            LocalDate eventDate,
            String region,
            String status,
            Long speakerId,
            String note,
            String dedupKey
    ) {
        return dsl.insertInto(ParsingEvents.PARSING_EVENTS)
                .set(ParsingEvents.PARSING_EVENTS.ID, id)
                .set(ParsingEvents.PARSING_EVENTS.CONFERENCE, conference)
                .set(ParsingEvents.PARSING_EVENTS.SPEAKER_NAME, speakerName)
                .set(ParsingEvents.PARSING_EVENTS.COMPANY, company)
                .set(ParsingEvents.PARSING_EVENTS.SOURCE_URL, sourceUrl)
                .set(ParsingEvents.PARSING_EVENTS.EVENT_DATE, eventDate)
                .set(ParsingEvents.PARSING_EVENTS.REGION, region)
                .set(ParsingEvents.PARSING_EVENTS.STATUS, status)
                .set(ParsingEvents.PARSING_EVENTS.SPEAKER_ID, speakerId)
                .set(ParsingEvents.PARSING_EVENTS.NOTE, note)
                .set(ParsingEvents.PARSING_EVENTS.DEDUP_KEY, dedupKey)
                .set(ParsingEvents.PARSING_EVENTS.CREATED_AT, LocalDateTime.now())
                .returning()
                .fetchOne();
    }

    public Optional<ParsingEventsRecord> findByDedupKey(String dedupKey) {
        return dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                .where(ParsingEvents.PARSING_EVENTS.DEDUP_KEY.eq(dedupKey))
                .fetchOptional();
    }

    public Optional<ParsingEventsRecord> findById(UUID id) {
        return dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                .where(ParsingEvents.PARSING_EVENTS.ID.eq(id))
                .fetchOptional();
    }

    public List<ParsingEventsRecord> findByFilters(LocalDate eventDate, String status) {
        var condition = DSL.noCondition();
        if (eventDate != null) {
            condition = condition.and(ParsingEvents.PARSING_EVENTS.EVENT_DATE.eq(eventDate));
        }
        if (status != null && !status.isBlank()) {
            condition = condition.and(ParsingEvents.PARSING_EVENTS.STATUS.eq(status));
        }
        return dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                .where(condition)
                .orderBy(ParsingEvents.PARSING_EVENTS.CREATED_AT.desc())
                .fetch();
    }

    public List<ParsingEventsRecord> findSpeakersByDate(LocalDate eventDate) {
        var condition = DSL.noCondition();
        if (eventDate != null) {
            condition = condition.and(ParsingEvents.PARSING_EVENTS.EVENT_DATE.eq(eventDate));
        }
        return dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                .where(condition)
                .orderBy(ParsingEvents.PARSING_EVENTS.CREATED_AT.desc())
                .fetch();
    }

    public Optional<ParsingEventsRecord> updateStatus(
            UUID id,
            String status,
            Long speakerId,
            String note
    ) {
        int updated = dsl.update(ParsingEvents.PARSING_EVENTS)
                .set(ParsingEvents.PARSING_EVENTS.STATUS, status)
                .set(ParsingEvents.PARSING_EVENTS.SPEAKER_ID, speakerId)
                .set(ParsingEvents.PARSING_EVENTS.NOTE, note)
                .where(ParsingEvents.PARSING_EVENTS.ID.eq(id))
                .execute();
        return updated > 0 ? findById(id) : Optional.empty();
    }

    public int countByStatus(String status) {
        return dsl.fetchCount(
                dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                        .where(ParsingEvents.PARSING_EVENTS.STATUS.eq(status))
        );
    }

    public List<ParsingEventsRecord> findAllOrderByCreatedAtDesc() {
        return dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                .orderBy(ParsingEvents.PARSING_EVENTS.CREATED_AT.desc())
                .fetch();
    }

    public List<ParsingEventsRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                .where(ParsingEvents.PARSING_EVENTS.SPEAKER_ID.eq(speakerId))
                .orderBy(ParsingEvents.PARSING_EVENTS.CREATED_AT.desc())
                .fetch();
    }

    public List<ParsingEventsRecord> findBySourceAndStatus(String sourceHost, String status) {
        var condition = DSL.noCondition();

        if (sourceHost != null && !sourceHost.isBlank()) {
            condition = condition.and(
                    ParsingEvents.PARSING_EVENTS.SOURCE_URL.likeIgnoreCase("%" + sourceHost + "%")
            );
        }

        if (status != null && !status.isBlank()) {
            condition = condition.and(ParsingEvents.PARSING_EVENTS.STATUS.eq(status));
        }

        return dsl.selectFrom(ParsingEvents.PARSING_EVENTS)
                .where(condition)
                .orderBy(ParsingEvents.PARSING_EVENTS.CREATED_AT.desc())
                .fetch();
    }

    public List<String> findDistinctRegions() {
        return dsl.selectDistinct(ParsingEvents.PARSING_EVENTS.REGION)
                .from(ParsingEvents.PARSING_EVENTS)
                .where(ParsingEvents.PARSING_EVENTS.REGION.isNotNull().and(ParsingEvents.PARSING_EVENTS.REGION.ne("")))
                .orderBy(ParsingEvents.PARSING_EVENTS.REGION.asc())
                .fetch(ParsingEvents.PARSING_EVENTS.REGION);
    }

    public List<String> findDistinctCompanies() {
        return dsl.selectDistinct(ParsingEvents.PARSING_EVENTS.COMPANY)
                .from(ParsingEvents.PARSING_EVENTS)
                .where(ParsingEvents.PARSING_EVENTS.COMPANY.isNotNull().and(ParsingEvents.PARSING_EVENTS.COMPANY.ne("")))
                .orderBy(ParsingEvents.PARSING_EVENTS.COMPANY.asc())
                .fetch(ParsingEvents.PARSING_EVENTS.COMPANY);
    }

    public record SourceCount(String source, int count) {
    }

    public List<String> findDistinctSources() {
        return dsl.selectDistinct(ParsingEvents.PARSING_EVENTS.SOURCE_URL)
                .from(ParsingEvents.PARSING_EVENTS)
                .orderBy(ParsingEvents.PARSING_EVENTS.SOURCE_URL.asc())
                .fetch(ParsingEvents.PARSING_EVENTS.SOURCE_URL);
    }

    public List<SourceCount> countGroupedBySource() {
        return dsl.select(
                        ParsingEvents.PARSING_EVENTS.SOURCE_URL,
                        DSL.count().as("cnt")
                )
                .from(ParsingEvents.PARSING_EVENTS)
                .groupBy(ParsingEvents.PARSING_EVENTS.SOURCE_URL)
                .orderBy(DSL.field("cnt", Integer.class).desc())
                .fetch(record -> new SourceCount(
                        record.get(ParsingEvents.PARSING_EVENTS.SOURCE_URL),
                        record.get("cnt", Integer.class)
                ));
    }
}
