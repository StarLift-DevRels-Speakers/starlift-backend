package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.TalkTags;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Talks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public List<String> findTagsByTalkId(UUID talkId) {
        return dsl.select(TalkTags.TALK_TAGS.TAG)
                .from(TalkTags.TALK_TAGS)
                .where(TalkTags.TALK_TAGS.TALK_ID.eq(talkId))
                .orderBy(TalkTags.TALK_TAGS.TAG.asc())
                .fetch(TalkTags.TALK_TAGS.TAG);
    }

    public Map<UUID, List<String>> findTagsByTalkIds(List<UUID> talkIds) {
        if (talkIds == null || talkIds.isEmpty()) {
            return Map.of();
        }

        return dsl.select(TalkTags.TALK_TAGS.TALK_ID, TalkTags.TALK_TAGS.TAG)
                .from(TalkTags.TALK_TAGS)
                .where(TalkTags.TALK_TAGS.TALK_ID.in(talkIds))
                .orderBy(TalkTags.TALK_TAGS.TALK_ID.asc(), TalkTags.TALK_TAGS.TAG.asc())
                .fetchGroups(TalkTags.TALK_TAGS.TALK_ID, TalkTags.TALK_TAGS.TAG)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void replaceTags(UUID talkId, List<String> tags) {
        dsl.deleteFrom(TalkTags.TALK_TAGS)
                .where(TalkTags.TALK_TAGS.TALK_ID.eq(talkId))
                .execute();
        addTags(talkId, tags);
    }

    public void addTags(UUID talkId, List<String> tags) {
        normalizeTags(tags).forEach(tag -> dsl.insertInto(TalkTags.TALK_TAGS)
                .set(TalkTags.TALK_TAGS.TALK_ID, talkId)
                .set(TalkTags.TALK_TAGS.TAG, tag)
                .onConflictDoNothing()
                .execute());
    }

    public boolean deleteTag(UUID talkId, String tag) {
        String normalizedTag = normalizeTag(tag);
        if (normalizedTag == null) {
            return false;
        }
        return dsl.deleteFrom(TalkTags.TALK_TAGS)
                .where(TalkTags.TALK_TAGS.TALK_ID.eq(talkId))
                .and(TalkTags.TALK_TAGS.TAG.eq(normalizedTag))
                .execute() > 0;
    }

    public boolean deleteById(UUID id) {
        return dsl.deleteFrom(Talks.TALKS)
                .where(Talks.TALKS.ID.eq(id))
                .execute() > 0;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .map(this::normalizeTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        String trimmed = tag.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
