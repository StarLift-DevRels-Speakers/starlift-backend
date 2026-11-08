package ru.shaxowskiy.javaspeakerclub.audience.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.EventInfo;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfileRepository.SaveProfile;
import ru.shaxowskiy.javaspeakerclub.events.EventRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventAudienceProfilesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventAudienceProfileServiceTest {

    private static final Long OWNER = 7L;
    private static final Long SPEAKER = 1L;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventAudienceInputsBuilder inputsBuilder = mock(EventAudienceInputsBuilder.class);
    private final EventAudienceProfileRepository repository = mock(EventAudienceProfileRepository.class);
    private final ProducerLlmClient llmClient = mock(ProducerLlmClient.class);
    private final SpeakerAccessGuard accessGuard = mock(SpeakerAccessGuard.class);
    private final ProducerJson json = new ProducerJson();
    private final AudienceProperties properties = AudienceTestData.properties();

    private EventAudienceProfileService service;
    private EventsRecord event;
    private EventAudienceInputs inputs;

    @BeforeEach
    void setUp() {
        service = new EventAudienceProfileService(eventRepository, inputsBuilder, new EventAudienceProfileValidator(), repository,
                new EventAudiencePrompt(properties), llmClient, json, properties, accessGuard);
        event = AudienceTestData.event(UUID.randomUUID(), OWNER, "published");
        inputs = new EventAudienceInputs(
                new EventInfo("JPoint 2026", LocalDate.of(2026, 12, 1), "Москва", "OFFLINE", "Java", "Senior", null),
                List.of(), List.of());
        when(eventRepository.findEvent(event.getId())).thenReturn(Optional.of(event));
        when(inputsBuilder.build(event)).thenReturn(inputs);
        when(llmClient.modelName()).thenReturn("GigaChat-2-Max");
        when(repository.save(any())).thenAnswer(call -> record(call.getArgument(0)));
    }

    @Test
    void computesAndStoresProfileWhenMissing() {
        when(repository.findByEventId(event.getId())).thenReturn(Optional.empty());
        when(llmClient.generate(anyString(), anyString(), eq(EventAudienceProfileDraft.class), anyInt())).thenReturn(draft());

        EventAudienceProfilesRecord result = service.ensure(event);

        ArgumentCaptor<SaveProfile> saved = ArgumentCaptor.forClass(SaveProfile.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(EventAudienceProfileService.STATUS_READY);
        assertThat(saved.getValue().inputsHash()).isEqualTo(service.inputsHash(inputs));
        assertThat(saved.getValue().promptVersion()).isEqualTo("event-audience-v1");
        assertThat(service.readProfile(result).levels()).containsEntry("senior", 80).containsEntry("middle", 20);
        // заявленная аудитория без программы — не выше medium
        assertThat(service.readProfile(result).confidence()).isEqualTo("medium");
    }

    @Test
    void reusesProfileWhenInputsUnchanged() {
        EventAudienceProfilesRecord stored = stored(EventAudienceProfileService.STATUS_READY, service.inputsHash(inputs), false);
        when(repository.findByEventId(event.getId())).thenReturn(Optional.of(stored));

        assertThat(service.ensure(event)).isSameAs(stored);
        verify(llmClient, never()).generate(anyString(), anyString(), any(), any());
    }

    @Test
    void recomputesWhenInputsChanged() {
        when(repository.findByEventId(event.getId())).thenReturn(Optional.of(stored(EventAudienceProfileService.STATUS_READY, "old-hash", false)));
        when(llmClient.generate(anyString(), anyString(), eq(EventAudienceProfileDraft.class), anyInt())).thenReturn(draft());

        service.ensure(event);

        verify(llmClient).generate(anyString(), anyString(), eq(EventAudienceProfileDraft.class), eq(2500));
    }

    @Test
    void manualProfileIsNeverOverwritten() {
        EventAudienceProfilesRecord manual = stored(EventAudienceProfileService.STATUS_READY, null, true);
        when(repository.findByEventId(event.getId())).thenReturn(Optional.of(manual));

        assertThat(service.ensure(event)).isSameAs(manual);
        verify(llmClient, never()).generate(anyString(), anyString(), any(), any());
    }

    @Test
    void failedProfileIsRetried() {
        when(repository.findByEventId(event.getId())).thenReturn(Optional.of(stored(EventAudienceProfileService.STATUS_FAILED, null, false)));
        when(llmClient.generate(anyString(), anyString(), eq(EventAudienceProfileDraft.class), anyInt())).thenReturn(draft());

        assertThat(service.ensure(event).getStatus()).isEqualTo(EventAudienceProfileService.STATUS_READY);
    }

    @Test
    void llmErrorIsStoredAsFailed() {
        when(repository.findByEventId(event.getId())).thenReturn(Optional.empty());
        when(llmClient.generate(anyString(), anyString(), eq(EventAudienceProfileDraft.class), anyInt()))
                .thenThrow(new IllegalStateException("timeout"));

        EventAudienceProfilesRecord result = service.ensure(event);

        assertThat(result.getStatus()).isEqualTo(EventAudienceProfileService.STATUS_FAILED);
        assertThat(result.getInputsHash()).isNull();
        assertThat(result.getError()).contains("timeout");
        assertThat(service.toResponse(result).message()).isNotBlank();
        assertThat(service.toResponse(result).profile()).isNull();
    }

    @Test
    void draftIsVisibleOnlyToOwner() {
        event.setState("draft");
        when(accessGuard.currentUserId()).thenReturn(SPEAKER);

        assertThatThrownBy(() -> service.get(event.getId())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void ownerSavesManualProfile() {
        when(accessGuard.currentUserId()).thenReturn(OWNER);

        EventAudienceProfileResponse response = service.update(event.getId(), new EventAudienceProfileUpdate(
                "Тимлиды", Map.of("lead", 100), List.of("тимлиды"), null, null, null, null));

        assertThat(response.manual()).isTrue();
        assertThat(response.profile().levels()).containsEntry("lead", 100);
        ArgumentCaptor<SaveProfile> saved = ArgumentCaptor.forClass(SaveProfile.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().manual()).isTrue();
    }

    @Test
    void otherUserCannotEditProfile() {
        when(accessGuard.currentUserId()).thenReturn(SPEAKER);

        assertThatThrownBy(() -> service.update(event.getId(), new EventAudienceProfileUpdate(null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.refresh(event.getId())).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void refreshRecomputesEvenManualProfile() {
        when(accessGuard.currentUserId()).thenReturn(OWNER);
        when(llmClient.generate(anyString(), anyString(), eq(EventAudienceProfileDraft.class), anyInt())).thenReturn(draft());

        EventAudienceProfileResponse response = service.refresh(event.getId());

        assertThat(response.manual()).isFalse();
        assertThat(response.status()).isEqualTo(EventAudienceProfileService.STATUS_READY);
    }

    @Test
    void warmUpSkipsDraftsAndSwallowsErrors() {
        event.setState("draft");
        service.warmUp(event.getId());
        verify(inputsBuilder, never()).build(any());

        event.setState("published");
        when(repository.findByEventId(event.getId())).thenThrow(new IllegalStateException("db down"));
        service.warmUp(event.getId());
    }

    private static EventAudienceProfileDraft draft() {
        return new EventAudienceProfileDraft("Опытные Java-разработчики", Map.of("senior", 80, "middle", 20),
                List.of("backend"), List.of("Java"), List.of("кейсы"), List.of("Spring"), List.of("основы"),
                "high", List.of(new EventAudienceProfileDraft.Evidence("EVENT", "audience")));
    }

    private EventAudienceProfilesRecord stored(String status, String hash, boolean manual) {
        EventAudienceProfilesRecord record = new EventAudienceProfilesRecord();
        record.setEventId(event.getId());
        record.setStatus(status);
        record.setInputsHash(hash);
        record.setManual(manual);
        record.setUpdatedDate(LocalDateTime.now());
        return record;
    }

    private static EventAudienceProfilesRecord record(SaveProfile save) {
        EventAudienceProfilesRecord record = new EventAudienceProfilesRecord();
        record.setEventId(save.eventId());
        record.setStatus(save.status());
        record.setProfileJson(save.profileJson());
        record.setInputsJson(save.inputsJson());
        record.setInputsHash(save.inputsHash());
        record.setValidationJson(save.validationJson());
        record.setManual(save.manual());
        record.setModel(save.model());
        record.setPromptVersion(save.promptVersion());
        record.setError(save.error());
        record.setUpdatedDate(LocalDateTime.now());
        return record;
    }
}
