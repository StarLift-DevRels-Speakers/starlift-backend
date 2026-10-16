package ru.shaxowskiy.javaspeakerclub.producer.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft.Evidence;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft.Step;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft.Theme;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanResponse;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerPlanRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class ProducerServiceTest {

    @Autowired
    private ProducerService producerService;

    @Autowired
    private ProducerPlanRepository planRepository;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private ProducerLlmClient llmClient;

    private List<String> feedbackIds;
    private UUID cfpId;

    @BeforeEach
    void setUp() {
        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        LocalDateTime now = LocalDateTime.now();

        data.user(1L, "anna", "Анна", "Левина", "federal", "8.8");
        data.user(2L, "timur", "Тимур", "Каримов", "local", null);
        data.user(3L, "viewer", "Вика", "Зрителева", "local", null);
        data.role(1L, "SPEAKER");
        data.role(2L, "SPEAKER");
        data.role(3L, "USER");

        UUID talk = data.talk(1L, "A/B-тесты без боли", now.minusDays(10));
        feedbackIds = List.of(
                data.feedback(talk, 10, "Живые кейсы из продакшена", "-", now.minusDays(9)).toString(),
                data.feedback(talk, 9, "Реальные цифры", "-", now.minusDays(9)).toString(),
                data.feedback(talk, 9, "Кейсы, а не теория", "-", now.minusDays(9)).toString()
        );
        cfpId = data.cfp("Mobile Frontier", "international", LocalDate.now().plusDays(30));
        data.link(1L, cfpId, 89);

        when(llmClient.modelName()).thenReturn("test-model");
    }

    @Test
    void storesValidatedPlanAndReturnsItAsLatest() {
        when(llmClient.generatePlan(anyString(), anyString())).thenReturn(new ProducerPlanDraft(
                "Растёшь, пора на международную сцену.",
                List.of(new Theme("Живые кейсы", feedbackIds)),
                List.of(new Theme("Выдуманная тема", List.of("no-such-feedback", "another", "third"))),
                List.of(new Step("Подать заявку на Mobile Frontier", "Уровень выше текущего", null,
                        List.of(new Evidence("CFP", cfpId.toString()))))
        ));

        ProducerPlanResponse response = producerService.regenerate(1L);

        assertThat(response.status()).isEqualTo(ProducerService.STATUS_READY);
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.promptVersion()).isEqualTo("producer-v1");
        assertThat(response.plan().strengths()).singleElement()
                .satisfies(theme -> assertThat(theme.mentions()).isEqualTo(3));
        assertThat(response.plan().growthAreas()).isEmpty();
        assertThat(response.plan().steps().getFirst().dueDate()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(response.validation().rejections()).isNotEmpty();
        assertThat(producerService.getLatest(1L).id()).isEqualTo(response.id());

        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(llmClient).generatePlan(anyString(), userMessage.capture());
        assertThat(userMessage.getValue()).startsWith("Сегодня: ").contains(feedbackIds.getFirst(), cfpId.toString());
    }

    @Test
    void skipsLlmWhenThereIsTooLittleData() {
        ProducerPlanResponse response = producerService.regenerate(2L);

        assertThat(response.status()).isEqualTo(ProducerService.STATUS_INSUFFICIENT_DATA);
        assertThat(response.message()).contains("мало данных");
        assertThat(response.plan()).isNull();
        verify(llmClient, never()).generatePlan(anyString(), anyString());
    }

    @Test
    void storesFailureWithoutLeakingErrorToClient() {
        when(llmClient.generatePlan(anyString(), anyString())).thenThrow(new IllegalStateException("provider is down"));

        ProducerPlanResponse response = producerService.regenerate(1L);

        assertThat(response.status()).isEqualTo(ProducerService.STATUS_FAILED);
        assertThat(response.message()).doesNotContain("provider is down");
        assertThat(planRepository.findLatestBySpeakerId(1L).orElseThrow().getError())
                .contains("IllegalStateException", "provider is down");
    }

    @Test
    void regenerateAllCoversOnlySpeakers() {
        when(llmClient.generatePlan(anyString(), anyString())).thenReturn(new ProducerPlanDraft(
                "…", List.of(new Theme("Живые кейсы", feedbackIds)), List.of(), List.of()));

        List<ProducerPlanResponse> responses = producerService.regenerateAll();

        assertThat(responses).extracting(ProducerPlanResponse::speakerId).containsExactly(1L, 2L);
    }
}
