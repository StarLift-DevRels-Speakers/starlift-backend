package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerAchievements;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerAchievementsRecord;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SpeakerAchievementRepository {

    private final DSLContext dsl;

    public record UpsertInput(
            UUID id,
            String title,
            String description,
            Integer currentValue,
            Integer targetValue
    ) {
    }

    public List<SpeakerAchievementsRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(SpeakerAchievements.SPEAKER_ACHIEVEMENTS)
                .where(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.SPEAKER_ID.eq(speakerId))
                .orderBy(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.CREATED_DATE.desc())
                .fetch();
    }

    public List<SpeakerAchievementsRecord> replaceForSpeaker(Long speakerId, List<UpsertInput> items) {
        List<SpeakerAchievementsRecord> existing = findBySpeakerId(speakerId);
        Set<UUID> keepIds = new HashSet<>();

        for (UpsertInput item : items) {
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            keepIds.add(id);

            boolean alreadyExists = existing.stream().anyMatch(record -> record.getId().equals(id));
            if (alreadyExists) {
                dsl.update(SpeakerAchievements.SPEAKER_ACHIEVEMENTS)
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TITLE, item.title())
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.DESCRIPTION, item.description())
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.CURRENT_VALUE, item.currentValue())
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TARGET_VALUE, item.targetValue())
                        .where(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.ID.eq(id))
                        .and(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.SPEAKER_ID.eq(speakerId))
                        .execute();
            } else {
                dsl.insertInto(SpeakerAchievements.SPEAKER_ACHIEVEMENTS)
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.ID, id)
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.SPEAKER_ID, speakerId)
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TITLE, item.title())
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.DESCRIPTION, item.description())
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.CURRENT_VALUE, item.currentValue())
                        .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TARGET_VALUE, item.targetValue())
                        .execute();
            }
        }

        dsl.deleteFrom(SpeakerAchievements.SPEAKER_ACHIEVEMENTS)
                .where(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.SPEAKER_ID.eq(speakerId))
                .and(keepIds.isEmpty()
                        ? SpeakerAchievements.SPEAKER_ACHIEVEMENTS.ID.isNotNull()
                        : SpeakerAchievements.SPEAKER_ACHIEVEMENTS.ID.notIn(keepIds))
                .execute();

        return findBySpeakerId(speakerId);
    }
}
