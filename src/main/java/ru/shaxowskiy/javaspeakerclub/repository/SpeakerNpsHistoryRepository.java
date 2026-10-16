package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerNpsHistory;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerNpsHistoryRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SpeakerNpsHistoryRepository {

    private final DSLContext dsl;

    public List<SpeakerNpsHistoryRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .where(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID.eq(speakerId))
                .orderBy(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT.desc())
                .fetch();
    }

    public List<SpeakerNpsHistoryRecord> findBySpeakerIds(List<Long> speakerIds) {
        if (speakerIds == null || speakerIds.isEmpty()) {
            return List.of();
        }

        return dsl.selectFrom(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .where(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID.in(speakerIds))
                .orderBy(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID.asc(), SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT.desc())
                .fetch();
    }

    public SpeakerNpsHistoryRecord findLatestBefore(Long speakerId, LocalDateTime beforeExclusive) {
        return dsl.selectFrom(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .where(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID.eq(speakerId))
                .and(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT.lt(beforeExclusive))
                .orderBy(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT.desc())
                .limit(1)
                .fetchOne();
    }

    public List<SpeakerNpsHistoryRecord> findBySpeakerIdAndMeasuredAtBetween(
            Long speakerId,
            LocalDateTime fromInclusive,
            LocalDateTime toInclusive
    ) {
        var condition = SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID.eq(speakerId);
        if (fromInclusive != null) {
            condition = condition.and(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT.ge(fromInclusive));
        }
        if (toInclusive != null) {
            condition = condition.and(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT.le(toInclusive));
        }

        return dsl.selectFrom(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .where(condition)
                .orderBy(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT.desc())
                .fetch();
    }

    public SpeakerNpsHistoryRecord create(Long speakerId, BigDecimal nps, LocalDateTime measuredAt, String source) {
        return dsl.insertInto(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID, speakerId)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.NPS, nps)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT, measuredAt)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SOURCE, source)
                .returning()
                .fetchOne();
    }
}
