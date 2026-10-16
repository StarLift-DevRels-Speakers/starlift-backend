package ru.shaxowskiy.javaspeakerclub.producer;

import org.jooq.DSLContext;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.CfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerCfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerNpsHistory;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.TalkFeedback;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Talks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Данные для интеграционных тестов продюсера. Удаление пользователей каскадно чистит их доклады, отзывы, роли и планы.
 */
public final class ProducerTestData {

    /**
     * Своя H2 для тестов с отдельным Spring-контекстом (@MockitoBean, MockMvc): общая testdb живёт весь прогон,
     * и повторный schema.sql в новом контексте падает на уже созданных таблицах.
     */
    public static final String ISOLATED_H2_URL =
            "spring.datasource.url=jdbc:h2:mem:producer-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    private final DSLContext dsl;

    public ProducerTestData(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void clean() {
        dsl.deleteFrom(Users.USERS).execute();
        dsl.deleteFrom(CfpRecommendations.CFP_RECOMMENDATIONS).execute();
    }

    public void user(long id, String username, String firstName, String lastName, String influence, String nps) {
        dsl.insertInto(Users.USERS)
                .set(Users.USERS.ID, id)
                .set(Users.USERS.USERNAME, username)
                .set(Users.USERS.PASSWORD, "pwd")
                .set(Users.USERS.FIRST_NAME, firstName)
                .set(Users.USERS.LAST_NAME, lastName)
                .set(Users.USERS.INFLUENCE, influence)
                .set(Users.USERS.NPS, nps == null ? null : new BigDecimal(nps))
                .execute();
    }

    public void role(long userId, String role) {
        dsl.insertInto(UserRoles.USER_ROLES)
                .set(UserRoles.USER_ROLES.USER_ID, userId)
                .set(UserRoles.USER_ROLES.ROLE, role)
                .execute();
    }

    public UUID talk(long speakerId, String topic, LocalDateTime date) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(Talks.TALKS)
                .set(Talks.TALKS.ID, id)
                .set(Talks.TALKS.SPEAKER_ID, speakerId)
                .set(Talks.TALKS.TOPIC, topic)
                .set(Talks.TALKS.TALK_DATE, date)
                .set(Talks.TALKS.CONFERENCE_TYPE, "CONFERENCE")
                .set(Talks.TALKS.FORMAT, "OFFLINE")
                .set(Talks.TALKS.ACTIVITY_NAME, "Test Conf")
                .set(Talks.TALKS.ACTIVITY_DATE, date)
                .execute();
        return id;
    }

    public UUID feedback(UUID talkId, int rating, String positive, String negative, LocalDateTime createdDate) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(TalkFeedback.TALK_FEEDBACK)
                .set(TalkFeedback.TALK_FEEDBACK.ID, id)
                .set(TalkFeedback.TALK_FEEDBACK.TALK_ID, talkId)
                .set(TalkFeedback.TALK_FEEDBACK.RATING, rating)
                .set(TalkFeedback.TALK_FEEDBACK.POSITIVE, positive)
                .set(TalkFeedback.TALK_FEEDBACK.NEGATIVE, negative)
                .set(TalkFeedback.TALK_FEEDBACK.IMPROVEMENT_SUGGESTIONS, "-")
                .set(TalkFeedback.TALK_FEEDBACK.CREATED_DATE, createdDate)
                .execute();
        return id;
    }

    public UUID cfp(String title, String level, LocalDate deadline) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(CfpRecommendations.CFP_RECOMMENDATIONS)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ID, id)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TITLE, title)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.CITY, "Москва")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.INFLUENCE_LEVEL, level)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TOPIC, "Product")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.DEADLINE, deadline)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY, "Host")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.URL, "https://example.com/" + title)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE, true)
                .execute();
        return id;
    }

    public void link(long speakerId, UUID cfpId, int matchScore) {
        dsl.insertInto(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS)
                .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID, speakerId)
                .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.RECOMMENDATION_ID, cfpId)
                .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.MATCH_SCORE, matchScore)
                .execute();
    }

    public void npsPoint(long speakerId, String nps, LocalDateTime measuredAt) {
        dsl.insertInto(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID, speakerId)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.NPS, new BigDecimal(nps))
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT, measuredAt)
                .execute();
    }
}
