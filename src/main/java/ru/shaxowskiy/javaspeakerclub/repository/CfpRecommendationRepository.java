package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.CfpRecommendationTags;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.CfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerCfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.CfpRecommendationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerCfpRecommendationsRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CfpRecommendationRepository {

    private final DSLContext dsl;

    public record SpeakerRecommendationRow(
            CfpRecommendationsRecord recommendation,
            Integer matchScore
    ) {
    }

    public record CfpRecommendationUpsertInput(
            String title,
            String city,
            String influenceLevel,
            String topic,
            LocalDate deadline,
            String hostCompany,
            String url,
            Boolean active,
            List<String> tags
    ) {
    }

    public record SpeakerCfpLinkInput(
            UUID recommendationId,
            Integer matchScore
    ) {
    }

    public List<SpeakerRecommendationRow> findBySpeakerId(Long speakerId) {
        return dsl.select(
                        CfpRecommendations.CFP_RECOMMENDATIONS.fields()
                )
                .select(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.MATCH_SCORE)
                .from(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS)
                .join(CfpRecommendations.CFP_RECOMMENDATIONS)
                .on(CfpRecommendations.CFP_RECOMMENDATIONS.ID.eq(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.RECOMMENDATION_ID))
                .where(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID.eq(speakerId))
                .and(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE.eq(true))
                .orderBy(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.MATCH_SCORE.desc())
                .fetch(record -> new SpeakerRecommendationRow(
                        record.into(CfpRecommendations.CFP_RECOMMENDATIONS),
                        record.get(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.MATCH_SCORE)
                ));
    }

    public Map<UUID, List<String>> findTagsByRecommendationIds(List<UUID> recommendationIds) {
        if (recommendationIds == null || recommendationIds.isEmpty()) {
            return Map.of();
        }

        return dsl.select(
                        CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.RECOMMENDATION_ID,
                        CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG
                )
                .from(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS)
                .where(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.RECOMMENDATION_ID.in(recommendationIds))
                .orderBy(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG.asc())
                .fetchGroups(
                        CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.RECOMMENDATION_ID,
                        CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG
                );
    }

    public List<String> findTagsByRecommendationId(UUID recommendationId) {
        return dsl.select(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG)
                .from(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS)
                .where(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.RECOMMENDATION_ID.eq(recommendationId))
                .orderBy(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG.asc())
                .fetch(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG);
    }

    public List<String> findDistinctCities() {
        return dsl.selectDistinct(CfpRecommendations.CFP_RECOMMENDATIONS.CITY)
                .from(CfpRecommendations.CFP_RECOMMENDATIONS)
                .where(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE.eq(true))
                .orderBy(CfpRecommendations.CFP_RECOMMENDATIONS.CITY.asc())
                .fetch(CfpRecommendations.CFP_RECOMMENDATIONS.CITY);
    }

    public List<String> findDistinctCompanies() {
        return dsl.selectDistinct(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY)
                .from(CfpRecommendations.CFP_RECOMMENDATIONS)
                .where(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE.eq(true))
                .orderBy(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY.asc())
                .fetch(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY);
    }

    public List<String> findDistinctTags() {
        return dsl.selectDistinct(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG)
                .from(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS)
                .orderBy(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG.asc())
                .fetch(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG);
    }

    public List<CfpRecommendationsRecord> findAll() {
        return dsl.selectFrom(CfpRecommendations.CFP_RECOMMENDATIONS)
                .orderBy(CfpRecommendations.CFP_RECOMMENDATIONS.CREATED_DATE.desc())
                .fetch();
    }

    public Optional<CfpRecommendationsRecord> findById(UUID recommendationId) {
        return dsl.selectFrom(CfpRecommendations.CFP_RECOMMENDATIONS)
                .where(CfpRecommendations.CFP_RECOMMENDATIONS.ID.eq(recommendationId))
                .fetchOptional();
    }

    public CfpRecommendationsRecord create(CfpRecommendationUpsertInput input) {
        CfpRecommendationsRecord record = dsl.insertInto(CfpRecommendations.CFP_RECOMMENDATIONS)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ID, UUID.randomUUID())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TITLE, input.title())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.CITY, input.city())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.INFLUENCE_LEVEL, input.influenceLevel())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TOPIC, input.topic())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.DEADLINE, input.deadline())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY, input.hostCompany())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.URL, input.url())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE, input.active())
                .returning()
                .fetchOne();

        replaceTags(record.getId(), input.tags());
        return record;
    }

    public Optional<CfpRecommendationsRecord> update(UUID recommendationId, CfpRecommendationUpsertInput input) {
        int updated = dsl.update(CfpRecommendations.CFP_RECOMMENDATIONS)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TITLE, input.title())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.CITY, input.city())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.INFLUENCE_LEVEL, input.influenceLevel())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TOPIC, input.topic())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.DEADLINE, input.deadline())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY, input.hostCompany())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.URL, input.url())
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE, input.active())
                .where(CfpRecommendations.CFP_RECOMMENDATIONS.ID.eq(recommendationId))
                .execute();

        if (updated == 0) {
            return Optional.empty();
        }

        replaceTags(recommendationId, input.tags());
        return findById(recommendationId);
    }

    public boolean softDelete(UUID recommendationId) {
        return dsl.update(CfpRecommendations.CFP_RECOMMENDATIONS)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE, false)
                .where(CfpRecommendations.CFP_RECOMMENDATIONS.ID.eq(recommendationId))
                .execute() > 0;
    }

    public void replaceTags(UUID recommendationId, List<String> tags) {
        dsl.deleteFrom(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS)
                .where(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.RECOMMENDATION_ID.eq(recommendationId))
                .execute();

        if (tags == null || tags.isEmpty()) {
            return;
        }

        tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .forEach(tag -> dsl.insertInto(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS)
                        .set(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.RECOMMENDATION_ID, recommendationId)
                        .set(CfpRecommendationTags.CFP_RECOMMENDATION_TAGS.TAG, tag)
                        .execute()
                );
    }

    public List<SpeakerCfpRecommendationsRecord> replaceSpeakerLinks(Long speakerId, List<SpeakerCfpLinkInput> links) {
        dsl.deleteFrom(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS)
                .where(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID.eq(speakerId))
                .execute();

        if (links != null) {
            links.stream()
                    .filter(link -> link.recommendationId() != null)
                    .forEach(link -> dsl.insertInto(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS)
                            .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID, speakerId)
                            .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.RECOMMENDATION_ID, link.recommendationId())
                            .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.MATCH_SCORE, link.matchScore())
                            .execute());
        }

        return dsl.selectFrom(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS)
                .where(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID.eq(speakerId))
                .orderBy(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.MATCH_SCORE.desc())
                .fetch();
    }

    public Map<Long, Integer> countRecommendationsBySpeakerIds(List<Long> speakerIds) {
        if (speakerIds == null || speakerIds.isEmpty()) {
            return Map.of();
        }

        return dsl.select(
                        SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID,
                        DSL.count().as("cnt")
                )
                .from(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS)
                .where(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID.in(speakerIds))
                .groupBy(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID)
                .fetchMap(
                        SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID,
                        record -> record.get("cnt", Integer.class)
                );
    }
}
