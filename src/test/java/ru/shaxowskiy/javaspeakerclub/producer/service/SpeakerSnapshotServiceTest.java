package ru.shaxowskiy.javaspeakerclub.producer.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpeakerSnapshotServiceTest {

    @Autowired
    private SpeakerSnapshotService snapshotService;

    @Autowired
    private DSLContext dsl;

    private final LocalDateTime now = LocalDateTime.now();
    private UUID annaFutureCfp;
    private UUID bobFeedback;

    @BeforeEach
    void setUp() {
        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        data.user(1L, "anna", "Анна", "Левина", "federal", "8.8");
        data.user(2L, "bob", "Боб", "Смирнов", "regional", "7.0");

        UUID recent = data.talk(1L, "A/B-тесты без боли", now.minusDays(10));
        UUID older = data.talk(1L, "Метрики продукта", now.minusDays(100));
        data.talk(1L, "Будущий доклад", now.plusDays(20));
        UUID bobTalk = data.talk(2L, "Чужой доклад", now.minusDays(5));

        data.feedback(recent, 10, "Живые кейсы", "-", now.minusDays(9));
        data.feedback(recent, 7, "Интересно", "Мало времени на вопросы", now.minusDays(9));
        data.feedback(older, 9, "Понятные схемы", "-", now.minusDays(99));
        data.feedback(older, 8, "Старый отзыв", "-", now.minusDays(400));
        bobFeedback = data.feedback(bobTalk, 5, "Чужой", "Чужой", now.minusDays(4));

        data.npsPoint(1L, "8.8", now.minusDays(1));
        data.npsPoint(1L, "8.2", now.minusDays(60));

        annaFutureCfp = data.cfp("Mobile Frontier", "international", LocalDate.now().plusDays(30));
        data.link(1L, annaFutureCfp, 89);
        data.link(1L, data.cfp("Old Conf", "federal", LocalDate.now().minusDays(1)), 70);
        data.link(2L, data.cfp("Bob Conf", "regional", LocalDate.now().plusDays(10)), 60);
    }

    @Test
    void buildsFactsOnlyFromSpeakersOwnData() {
        SpeakerSnapshot snapshot = snapshotService.build(1L);

        assertThat(snapshot.speaker().name()).isEqualTo("Анна Левина");
        assertThat(snapshot.speaker().stage()).isEqualTo("federal");
        assertThat(snapshot.speaker().nextStage()).isEqualTo("international");

        assertThat(snapshot.activity().talksTotal()).isEqualTo(2);
        assertThat(snapshot.activity().talksLast90Days()).isEqualTo(1);
        assertThat(snapshot.activity().talksPrevious90Days()).isEqualTo(1);
        assertThat(snapshot.activity().momentum()).isEqualTo("stable");
        assertThat(snapshot.activity().daysSinceLastTalk()).isEqualTo(10);
        assertThat(snapshot.talks()).hasSize(3);

        assertThat(snapshot.feedback()).hasSize(3);
        assertThat(snapshot.feedback()).extracting(SpeakerSnapshot.FeedbackItem::id)
                .doesNotContain(bobFeedback.toString());
        assertThat(snapshot.feedbackStats().count()).isEqualTo(3);
        assertThat(snapshot.feedbackStats().promotersPercent()).isEqualTo(67);

        assertThat(snapshot.nps().current()).isEqualTo(88);
        assertThat(snapshot.nps().previous()).isEqualTo(82);
        assertThat(snapshot.nps().delta()).isEqualTo(6);

        assertThat(snapshot.cfps()).extracting(SpeakerSnapshot.CfpItem::id)
                .containsExactly(annaFutureCfp.toString());
        assertThat(snapshot.cfps().getFirst().level()).isEqualTo("international");
    }

    @Test
    void momentumComparesLastAndPrevious90Days() {
        assertThat(SpeakerSnapshotService.momentum(0, 0)).isEqualTo("inactive");
        assertThat(SpeakerSnapshotService.momentum(3, 1)).isEqualTo("growing");
        assertThat(SpeakerSnapshotService.momentum(0, 2)).isEqualTo("declining");
        assertThat(SpeakerSnapshotService.momentum(2, 2)).isEqualTo("stable");
    }
}
