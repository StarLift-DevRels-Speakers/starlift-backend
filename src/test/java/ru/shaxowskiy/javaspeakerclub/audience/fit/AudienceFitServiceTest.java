package ru.shaxowskiy.javaspeakerclub.audience.fit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitCheckRepository.Completion;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Criterion;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Issue;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitModels.AudienceFitCheckResponse;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitModels.AudienceFitRequest;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialAssembler;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialExtractor;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfile;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfileService;
import ru.shaxowskiy.javaspeakerclub.audience.trigger.AudienceTriggers.FitCheckRequested;
import ru.shaxowskiy.javaspeakerclub.events.EventRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.AudienceFitChecksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventApplicationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventAudienceProfilesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerTalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UserMediaRecord;
import ru.shaxowskiy.javaspeakerclub.notification.NotificationService;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerTalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserMediaRepository;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;
import ru.shaxowskiy.javaspeakerclub.service.MinioService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudienceFitServiceTest {

    private static final Long SPEAKER = 1L;
    private static final Long OTHER = 2L;
    private static final Long OWNER = 7L;
    private static final String LONG_DESCRIPTION = "Разбираем virtual threads на реальном сервисе: ".repeat(6);

    private final AudienceFitCheckRepository checkRepository = mock(AudienceFitCheckRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final SpeakerTalkRepository talkRepository = mock(SpeakerTalkRepository.class);
    private final UserMediaRepository mediaRepository = mock(UserMediaRepository.class);
    private final MinioService minioService = mock(MinioService.class);
    private final EventAudienceProfileService profileService = mock(EventAudienceProfileService.class);
    private final ProducerLlmClient llmClient = mock(ProducerLlmClient.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final SpeakerAccessGuard accessGuard = mock(SpeakerAccessGuard.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ProducerJson json = new ProducerJson();
    private final AudienceProperties properties = AudienceTestData.properties();

    private AudienceFitService service;
    private EventsRecord event;
    private SpeakerTalksRecord talk;
    private EventAudienceProfilesRecord profileRecord;

    @BeforeEach
    void setUp() {
        service = new AudienceFitService(checkRepository, eventRepository, talkRepository, mediaRepository, minioService,
                new MaterialExtractor(properties), new MaterialAssembler(properties), profileService,
                new AudienceFitValidator(properties), new AudienceFitPrompt(properties), llmClient, json,
                notificationService, accessGuard, events, properties);

        event = AudienceTestData.event(UUID.randomUUID(), OWNER, "published");
        talk = AudienceTestData.talk(UUID.randomUUID(), SPEAKER, LONG_DESCRIPTION);
        when(eventRepository.findEvent(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.findByIds(anyList())).thenReturn(List.of(event));
        when(talkRepository.findById(talk.getId())).thenReturn(Optional.of(talk));
        when(talkRepository.findByIds(anyList())).thenReturn(List.of(talk));
        when(mediaRepository.findByIds(anyList())).thenReturn(List.of());
        when(mediaRepository.findByTalkId(any())).thenReturn(List.of());
        when(llmClient.modelName()).thenReturn("GigaChat-2-Max");

        profileRecord = new EventAudienceProfilesRecord();
        profileRecord.setEventId(event.getId());
        profileRecord.setStatus("ready");
        profileRecord.setUpdatedDate(LocalDateTime.now().minusHours(1));
        when(profileService.ensure(event)).thenReturn(profileRecord);
        when(profileService.readProfile(profileRecord)).thenReturn(profile());
        when(checkRepository.findReadyByContent(any(), anyString(), any())).thenReturn(Optional.empty());
        when(checkRepository.insertPending(any(), any(), any(), any(), anyString()))
                .thenAnswer(call -> pending(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)));
    }

    // ---------- request ----------

    @Test
    void requestCreatesPendingCheckAndStartsIt() {
        when(accessGuard.currentUserId()).thenReturn(SPEAKER);

        AudienceFitCheckResponse response = service.request(new AudienceFitRequest(event.getId(), talk.getId(), null));

        assertThat(response.status()).isEqualTo(AudienceFitService.STATUS_PENDING);
        assertThat(response.message()).isNotBlank();
        assertThat(response.eventName()).isEqualTo("JPoint 2026");
        assertThat(response.talkName()).isEqualTo(talk.getName());
        verify(checkRepository).insertPending(event.getId(), SPEAKER, talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);
        verify(events).publishEvent(any(FitCheckRequested.class));
    }

    @Test
    void requestTakesTalkFromMedia() {
        when(accessGuard.currentUserId()).thenReturn(SPEAKER);
        UserMediaRecord media = media("slides.pptx", talk.getId());
        when(mediaRepository.findByIdAndUserId(media.getId(), SPEAKER)).thenReturn(Optional.of(media));

        service.request(new AudienceFitRequest(event.getId(), null, media.getId()));

        verify(checkRepository).insertPending(event.getId(), SPEAKER, talk.getId(), media.getId(), AudienceFitService.TRIGGER_MANUAL);
    }

    @Test
    void requestRejectsForeignTalkUnsupportedFileAndDraftEvent() {
        when(accessGuard.currentUserId()).thenReturn(OTHER);
        assertThatThrownBy(() -> service.request(new AudienceFitRequest(event.getId(), talk.getId(), null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Доклад не найден");

        UserMediaRecord video = media("demo.mp4", null);
        when(mediaRepository.findByIdAndUserId(video.getId(), OTHER)).thenReturn(Optional.of(video));
        assertThatThrownBy(() -> service.request(new AudienceFitRequest(event.getId(), null, video.getId())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PDF");

        assertThatThrownBy(() -> service.request(new AudienceFitRequest(event.getId(), null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Выберите доклад или файл");

        event.setState("draft");
        assertThatThrownBy(() -> service.request(new AudienceFitRequest(event.getId(), talk.getId(), null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Мероприятие не найдено");

        verify(checkRepository, never()).insertPending(any(), any(), any(), any(), anyString());
    }

    // ---------- run ----------

    @Test
    void runValidatesLlmAnswerAndStoresResult() {
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);
        when(llmClient.generate(anyString(), anyString(), eq(AudienceFitDraft.class), anyInt())).thenReturn(new AudienceFitDraft(
                "fits", "Подходит после правок",
                List.of(new Criterion("level", 9, null), new Criterion("relevance", 9, null)),
                List.of(new Issue("talk-description", "Мало цифр", "Добавь метрики"), new Issue("slide-99", "Нет такого", null)),
                List.of()));

        service.run(check.getId());

        Completion completion = completion(check);
        assertThat(completion.status()).isEqualTo(AudienceFitService.STATUS_READY);
        assertThat(completion.contentHash()).hasSize(64);
        assertThat(completion.promptVersion()).isEqualTo("audience-fit-v2");
        AudienceFitResult result = json.read(completion.resultJson(), AudienceFitResult.class);
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.verdict()).isEqualTo(AudienceFitValidator.FITS);
        assertThat(result.issues()).singleElement().extracting(AudienceFitResult.Issue::label).isEqualTo("Описание доклада");
        assertThat(result.profileConfidence()).isEqualTo("high");
        assertThat(completion.validationJson()).contains("slide-99");
        // ручную проверку спикер ждёт на экране — без уведомления
        verify(notificationService, never()).notify(any(), anyString());
    }

    @Test
    void runSendsProfileAndFragmentsToModel() {
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);
        when(llmClient.generate(anyString(), anyString(), eq(AudienceFitDraft.class), anyInt()))
                .thenReturn(new AudienceFitDraft(null, null, List.of(new Criterion("level", 5, null)), null, null));

        service.run(check.getId());

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llmClient).generate(anyString(), user.capture(), eq(AudienceFitDraft.class), eq(2500));
        assertThat(user.getValue())
                .contains("«JPoint 2026»")
                .contains("Senior Java-разработчики")
                .contains("talk-description");
    }

    @Test
    void runExtractsUploadedPresentationAndNotifiesOnAutoTrigger() throws IOException {
        UserMediaRecord media = media("speech.md", talk.getId());
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(minioService.getFile(media.getMediaMinioKey()))
                .thenReturn(new ByteArrayInputStream("Слайд про основы Java\n\nКейс 40k RPS".getBytes(StandardCharsets.UTF_8)));
        AudienceFitChecksRecord check = stored(talk.getId(), media.getId(), AudienceFitService.TRIGGER_UPLOAD);
        when(llmClient.generate(anyString(), anyString(), eq(AudienceFitDraft.class), anyInt()))
                .thenReturn(new AudienceFitDraft("not_fits", null, List.of(new Criterion("level", 3, null)), null, null));

        service.run(check.getId());

        Completion completion = completion(check);
        assertThat(completion.mediaId()).isEqualTo(media.getId());
        assertThat(completion.materialJson()).contains("part-1").contains("Кейс 40k RPS");
        verify(notificationService).notify(eq(SPEAKER), argThat(text ->
                text.contains("speech.md") && text.contains("JPoint 2026") && text.contains("не подходит (30/100)")));
    }

    @Test
    void runPicksLatestSupportedFileOfTalk() throws IOException {
        UserMediaRecord video = media("demo.mp4", talk.getId());
        UserMediaRecord slides = media("slides.txt", talk.getId());
        when(mediaRepository.findByTalkId(talk.getId())).thenReturn(List.of(video, slides));
        when(minioService.getFile(slides.getMediaMinioKey()))
                .thenReturn(new ByteArrayInputStream("Текст слайдов".getBytes(StandardCharsets.UTF_8)));
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_APPLICATION);
        when(llmClient.generate(anyString(), anyString(), eq(AudienceFitDraft.class), anyInt()))
                .thenReturn(new AudienceFitDraft(null, null, List.of(new Criterion("level", 8, null)), null, null));

        service.run(check.getId());

        assertThat(completion(check).mediaId()).isEqualTo(slides.getId());
    }

    @Test
    void runMarksThinMaterialAsInsufficientWithoutLlm() {
        talk.setDescription("Коротко");
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);

        service.run(check.getId());

        Completion completion = completion(check);
        assertThat(completion.status()).isEqualTo(AudienceFitService.STATUS_INSUFFICIENT_DATA);
        assertThat(completion.error()).contains("описание доклада");
        verify(llmClient, never()).generate(anyString(), anyString(), any(), any());
        verify(profileService, never()).ensure(any());
    }

    @Test
    void runExplainsUnreadableFile() {
        talk.setDescription(null);
        UserMediaRecord old = media("old.ppt", talk.getId());
        when(mediaRepository.findById(old.getId())).thenReturn(Optional.of(old));
        AudienceFitChecksRecord check = stored(talk.getId(), old.getId(), AudienceFitService.TRIGGER_MANUAL);

        service.run(check.getId());

        assertThat(completion(check).error()).contains("old.ppt").contains("PPTX");
    }

    @Test
    void runReusesCachedResultForSameMaterial() {
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_APPLICATION);
        AudienceFitChecksRecord cached = new AudienceFitChecksRecord();
        cached.setResultJson(json.write(new AudienceFitResult("fits", 85, null, List.of(), List.of(), List.of(), "high", false)));
        cached.setModel("GigaChat-2-Max");
        cached.setPromptVersion("audience-fit-v2");
        when(checkRepository.findReadyByContent(eq(event.getId()), anyString(), eq(profileRecord.getUpdatedDate())))
                .thenReturn(Optional.of(cached));

        service.run(check.getId());

        assertThat(completion(check).resultJson()).isEqualTo(cached.getResultJson());
        verify(llmClient, never()).generate(anyString(), anyString(), any(), any());
        verify(notificationService).notify(eq(SPEAKER), argThat(text -> text.contains("подходит (85/100)")));
    }

    @Test
    void runFailsWhenProfileUnavailable() {
        when(profileService.readProfile(profileRecord)).thenReturn(null);
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);

        service.run(check.getId());

        assertThat(completion(check).status()).isEqualTo(AudienceFitService.STATUS_FAILED);
        verify(llmClient, never()).generate(anyString(), anyString(), any(), any());
    }

    @Test
    void runStoresLlmErrorAsFailed() {
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);
        when(llmClient.generate(anyString(), anyString(), eq(AudienceFitDraft.class), anyInt()))
                .thenThrow(new IllegalStateException("GigaChat 500"));

        service.run(check.getId());

        Completion completion = completion(check);
        assertThat(completion.status()).isEqualTo(AudienceFitService.STATUS_FAILED);
        assertThat(completion.error()).contains("GigaChat 500");
    }

    @Test
    void runIgnoresFinishedChecks() {
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);
        check.setStatus(AudienceFitService.STATUS_READY);

        service.run(check.getId());

        verify(checkRepository, never()).complete(any(), any());
    }

    // ---------- triggers ----------

    @Test
    void uploadedMediaIsCheckedForUpcomingEventsOfTheTalk() {
        UserMediaRecord media = media("slides.pdf", talk.getId());
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        EventsRecord past = AudienceTestData.event(UUID.randomUUID(), OWNER, "published");
        past.setEventDate(LocalDate.now().minusDays(1));
        EventApplicationsRecord upcomingApplication = application(event.getId());
        EventApplicationsRecord pastApplication = application(past.getId());
        when(eventRepository.findApplicationsByTalk(SPEAKER, talk.getId(), AudienceFitService.ACTIVE_APPLICATION_STATUSES))
                .thenReturn(List.of(upcomingApplication, pastApplication, upcomingApplication));
        when(eventRepository.findByIds(List.of(event.getId(), past.getId()))).thenReturn(List.of(event, past));

        service.checkUploadedMedia(SPEAKER, media.getId(), talk.getId());

        verify(checkRepository, times(1)).insertPending(event.getId(), SPEAKER, talk.getId(), media.getId(), AudienceFitService.TRIGGER_UPLOAD);
        verify(checkRepository, never()).insertPending(eq(past.getId()), any(), any(), any(), anyString());
    }

    @Test
    void uploadedVideoIsNotChecked() {
        UserMediaRecord video = media("demo.mp4", talk.getId());
        when(mediaRepository.findById(video.getId())).thenReturn(Optional.of(video));

        service.checkUploadedMedia(SPEAKER, video.getId(), talk.getId());

        verify(eventRepository, never()).findApplicationsByTalk(any(), any(), anyList());
    }

    // ---------- reading ----------

    @Test
    void devrelSeesReadyChecksOfOwnEvent() {
        when(accessGuard.currentUserId()).thenReturn(OWNER);
        when(accessGuard.isDevrel()).thenReturn(true);

        service.find(event.getId(), null, null);

        verify(checkRepository).findReadyByEvent(event.getId(), 100);
        verify(checkRepository, never()).findBySpeaker(any(), any(), any(), any(), anyInt());
    }

    @Test
    void speakerSeesOwnChecks() {
        when(accessGuard.currentUserId()).thenReturn(SPEAKER);
        when(accessGuard.isDevrel()).thenReturn(false);

        service.find(event.getId(), talk.getId(), null);

        verify(checkRepository).findBySpeaker(SPEAKER, event.getId(), talk.getId(), null, 50);
    }

    @Test
    void foreignCheckIsHidden() {
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);
        when(accessGuard.currentUserId()).thenReturn(OTHER);
        when(accessGuard.isDevrel()).thenReturn(false);

        assertThatThrownBy(() -> service.get(check.getId())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void stalePendingCheckIsShownAsFailed() {
        AudienceFitChecksRecord check = stored(talk.getId(), null, AudienceFitService.TRIGGER_MANUAL);
        check.setCreatedDate(LocalDateTime.now().minusMinutes(30));

        AudienceFitCheckResponse response = service.toResponses(List.of(check)).getFirst();

        assertThat(response.status()).isEqualTo(AudienceFitService.STATUS_FAILED);
        assertThat(response.message()).contains("прервалась");
    }

    @Test
    void fileNameFallsBackToObjectKey() {
        UserMediaRecord media = new UserMediaRecord();
        media.setMediaMinioKey("user-media/1/0b7c_old slides.pptx");

        assertThat(AudienceFitService.fileName(media)).isEqualTo("old slides.pptx");
    }

    private Completion completion(AudienceFitChecksRecord check) {
        ArgumentCaptor<Completion> completion = ArgumentCaptor.forClass(Completion.class);
        verify(checkRepository).complete(eq(check.getId()), completion.capture());
        return completion.getValue();
    }

    private AudienceFitChecksRecord stored(UUID talkId, UUID mediaId, String trigger) {
        AudienceFitChecksRecord check = pending(event.getId(), SPEAKER, talkId, mediaId, trigger);
        when(checkRepository.findById(check.getId())).thenReturn(Optional.of(check));
        return check;
    }

    private static AudienceFitChecksRecord pending(UUID eventId, Long speakerId, UUID talkId, UUID mediaId, String trigger) {
        AudienceFitChecksRecord check = new AudienceFitChecksRecord();
        check.setId(UUID.randomUUID());
        check.setEventId(eventId);
        check.setSpeakerId(speakerId);
        check.setSpeakerTalkId(talkId);
        check.setMediaId(mediaId);
        check.setTriggerSource(trigger);
        check.setStatus(AudienceFitService.STATUS_PENDING);
        check.setCreatedDate(LocalDateTime.now());
        check.setUpdatedDate(LocalDateTime.now());
        return check;
    }

    private static UserMediaRecord media(String name, UUID talkId) {
        UserMediaRecord media = new UserMediaRecord();
        media.setId(UUID.randomUUID());
        media.setUserId(SPEAKER);
        media.setOriginalName(name);
        media.setMediaMinioKey("user-media/1/" + UUID.randomUUID() + "_" + name);
        media.setSpeakerTalkId(talkId);
        return media;
    }

    private static EventApplicationsRecord application(UUID eventId) {
        EventApplicationsRecord application = new EventApplicationsRecord();
        application.setEventId(eventId);
        return application;
    }

    private static EventAudienceProfile profile() {
        return new EventAudienceProfile("Senior Java-разработчики", Map.of("senior", 80, "middle", 20),
                List.of("backend"), List.of("Java"), List.of("кейсы"), List.of("Spring"), List.of("основы"), "high", List.of());
    }
}
