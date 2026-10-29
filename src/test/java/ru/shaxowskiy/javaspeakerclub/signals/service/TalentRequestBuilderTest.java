package ru.shaxowskiy.javaspeakerclub.signals.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.ParsingEvents;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerAchievements;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserProfileTags;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TalentRequestBuilderTest {

    @Autowired
    private TalentRequestBuilder builder;

    @Autowired
    private DSLContext dsl;

    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        data.user(1L, "anna", "Анна", "Левина", "federal", "8.8");
        data.user(2L, "timur", "Тимур", "Каримов", "local", null);

        dsl.update(Users.USERS)
                .set(Users.USERS.REGION, "Екатеринбург")
                .set(Users.USERS.COMPANY, "Контур")
                .set(Users.USERS.COMPANY_ROLE, "Product Manager")
                .set(Users.USERS.ABOUT, "Продакт, выступаю про метрики")
                .set(Users.USERS.PROFILE_COMPLETION, 85)
                .where(Users.USERS.ID.eq(1L))
                .execute();

        UUID recent = data.talk(1L, "A/B-тесты без боли", now.minusDays(10));
        UUID older = data.talk(1L, "Метрики продукта", now.minusDays(200));
        data.talk(1L, "Будущий доклад", now.plusDays(20));
        UUID timurTalk = data.talk(2L, "Чужой доклад", now.minusDays(5));

        data.feedback(recent, 10, "Живые кейсы", "-", now.minusDays(9));
        data.feedback(recent, 7, "Интересно", "Мало времени на вопросы", now.minusDays(9));
        data.feedback(older, 8, "Старый отзыв", "-", now.minusDays(400));
        data.feedback(timurTalk, 2, "Чужой", "Чужой", now.minusDays(4));

        achievement(1L, "10 докладов за год", 4, 10);
        achievement(1L, "Блог", 120, 100);

        tag(1L, "metrics");
        tag(1L, "product");

        parsingEvent(1L, "Product Camp", "new", LocalDate.now().minusDays(3));
        parsingEvent(1L, "Подтверждённая конфа", "confirmed", LocalDate.now().minusDays(30));
        parsingEvent(1L, "Отклонённая конфа", "rejected", LocalDate.now().minusDays(40));

        data.link(1L, data.cfp("High Open", "federal", LocalDate.now().plusDays(30)), 85);
        data.link(1L, data.cfp("Threshold Open", "federal", LocalDate.now().plusDays(30)), 70);
        data.link(1L, data.cfp("Low Open", "federal", LocalDate.now().plusDays(30)), 69);
        data.link(1L, data.cfp("High Expired", "federal", LocalDate.now().minusDays(1)), 95);
    }

    @Test
    void buildsRequestFromSpeakersOwnData() {
        TalentRequest request = builder.build(1L);

        assertThat(request.id()).isEqualTo(1L);
        assertThat(request.username()).isEqualTo("anna");
        assertThat(request.region()).isEqualTo("Екатеринбург");
        assertThat(request.company()).isEqualTo("Контур");
        assertThat(request.companyRole()).isEqualTo("Product Manager");
        assertThat(request.about()).isEqualTo("Продакт, выступаю про метрики");
        assertThat(request.influence()).isEqualTo("federal");
        assertThat(request.nps()).isEqualByComparingTo(new BigDecimal("8.8"));
        assertThat(request.profileCompletion()).isEqualTo(85);

        assertThat(request.talksTotal()).isEqualTo(2);
        assertThat(request.talksLast3Months()).isEqualTo(1);
        assertThat(request.lastTalkDate()).isEqualToIgnoringNanos(now.minusDays(10));
        assertThat(request.recentTalks()).extracting(TalentRequest.TalkItem::topic)
                .containsExactly("A/B-тесты без боли", "Метрики продукта");

        assertThat(request.feedbackCount()).isEqualTo(3);
        assertThat(request.avgRating()).isEqualTo(8.33);
        assertThat(request.recentFeedback()).extracting(TalentRequest.FeedbackItem::positive)
                .doesNotContain("Чужой");

        assertThat(request.achievementsProgressPct()).isEqualTo(70.0);
        assertThat(request.achievements()).hasSize(2);
        assertThat(request.cfpHighMatchCount()).isEqualTo(2);
        assertThat(request.profileTags()).containsExactly("metrics", "product");

        assertThat(request.parsingEvents()).extracting(TalentRequest.ParsingEventItem::conference)
                .containsExactly("Product Camp");
    }

    @Test
    void sendsNullWhenDataIsMissing() {
        TalentRequest request = builder.build(2L);

        assertThat(request.region()).isNull();
        assertThat(request.company()).isNull();
        assertThat(request.companyRole()).isNull();
        assertThat(request.about()).isNull();
        assertThat(request.nps()).isNull();
        assertThat(request.avgRating()).isEqualTo(2.0);
        assertThat(request.achievementsProgressPct()).isBetween(30.0, 70.0);
        assertThat(request.achievements()).isEmpty();
        assertThat(request.parsingEvents()).isEmpty();
        assertThat(request.profileTags()).isEmpty();
        assertThat(request.cfpHighMatchCount()).isZero();
    }

    @Test
    void speakerWithoutFeedbackGetsZeroRating() {
        new ProducerTestData(dsl).user(3L, "newbie", null, null, "local", null);

        TalentRequest request = builder.build(3L);

        assertThat(request.talksTotal()).isZero();
        assertThat(request.lastTalkDate()).isNull();
        assertThat(request.feedbackCount()).isZero();
        assertThat(request.avgRating()).isZero();
    }

    @Test
    void placeholderStaysWithinThreeToSeven() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(TalentRequestBuilder.placeholder(1)).isBetween(3.0, 7.0);
        }
    }

    @Test
    void unknownSpeakerIsNotFound() {
        assertThatThrownBy(() -> builder.build(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private void achievement(long speakerId, String title, int current, int target) {
        dsl.insertInto(SpeakerAchievements.SPEAKER_ACHIEVEMENTS)
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.ID, UUID.randomUUID())
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.SPEAKER_ID, speakerId)
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TITLE, title)
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.DESCRIPTION, "-")
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.CURRENT_VALUE, current)
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TARGET_VALUE, target)
                .execute();
    }

    private void tag(long userId, String tag) {
        dsl.insertInto(UserProfileTags.USER_PROFILE_TAGS)
                .set(UserProfileTags.USER_PROFILE_TAGS.USER_ID, userId)
                .set(UserProfileTags.USER_PROFILE_TAGS.TAG, tag)
                .execute();
    }

    private void parsingEvent(long speakerId, String conference, String status, LocalDate eventDate) {
        dsl.insertInto(ParsingEvents.PARSING_EVENTS)
                .set(ParsingEvents.PARSING_EVENTS.ID, UUID.randomUUID())
                .set(ParsingEvents.PARSING_EVENTS.CONFERENCE, conference)
                .set(ParsingEvents.PARSING_EVENTS.SPEAKER_NAME, "Анна Левина")
                .set(ParsingEvents.PARSING_EVENTS.SOURCE_URL, "https://example.com/" + conference)
                .set(ParsingEvents.PARSING_EVENTS.EVENT_DATE, eventDate)
                .set(ParsingEvents.PARSING_EVENTS.STATUS, status)
                .set(ParsingEvents.PARSING_EVENTS.SPEAKER_ID, speakerId)
                .set(ParsingEvents.PARSING_EVENTS.DEDUP_KEY, UUID.randomUUID().toString().substring(0, 32))
                .execute();
    }
}
