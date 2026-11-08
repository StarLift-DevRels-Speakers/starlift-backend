package ru.shaxowskiy.javaspeakerclub.audience.profile;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;
import ru.shaxowskiy.javaspeakerclub.events.EventRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventApplicationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ParsingEventsRecord;
import ru.shaxowskiy.javaspeakerclub.repository.ParsingEventRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerTalkRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventAudienceInputsBuilderTest {

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final SpeakerTalkRepository talkRepository = mock(SpeakerTalkRepository.class);
    private final ParsingEventRepository parsingRepository = mock(ParsingEventRepository.class);
    private final EventAudienceInputsBuilder builder = new EventAudienceInputsBuilder(
            eventRepository, talkRepository, parsingRepository, AudienceTestData.properties());

    @Test
    void conferenceTermDropsYearAndPunctuation() {
        assertThat(EventAudienceInputsBuilder.conferenceTerm("JPoint 2026")).isEqualTo("jpoint");
        assertThat(EventAudienceInputsBuilder.conferenceTerm("Heisenbug: Москва, 2025")).isEqualTo("heisenbug москва");
        assertThat(EventAudienceInputsBuilder.conferenceTerm("DevOops-2024")).isEqualTo("devoops");
        assertThat(EventAudienceInputsBuilder.conferenceTerm("2026")).isNull();
        assertThat(EventAudienceInputsBuilder.conferenceTerm(null)).isNull();
    }

    @Test
    void collectsEventFieldsAcceptedTalksAndPastEditions() {
        UUID eventId = UUID.randomUUID();
        UUID talkId = UUID.randomUUID();
        EventsRecord event = AudienceTestData.event(eventId, 7L, "published");
        event.setDescription("   ");

        EventApplicationsRecord accepted = new EventApplicationsRecord();
        accepted.setSpeakerTalkId(talkId);
        EventApplicationsRecord withoutTalk = new EventApplicationsRecord();
        when(eventRepository.findApplicationsByEvent(eventId, "accepted")).thenReturn(List.of(accepted, withoutTalk));
        when(talkRepository.findByIds(List.of(talkId))).thenReturn(List.of(AudienceTestData.talk(talkId, 2L, "Про Loom")));

        ParsingEventsRecord past = new ParsingEventsRecord();
        past.setId(UUID.randomUUID());
        past.setConference("JPoint 2025");
        past.setSpeakerName("Иван Петров");
        past.setCompany("Яндекс");
        past.setEventDate(LocalDate.of(2025, 4, 1));
        when(parsingRepository.findByConferenceContaining("jpoint", 30)).thenReturn(List.of(past));

        EventAudienceInputs inputs = builder.build(event);

        assertThat(inputs.event().audience()).isEqualTo("Senior Java-разработчики");
        assertThat(inputs.event().description()).isNull();
        assertThat(inputs.acceptedTalks()).singleElement().satisfies(talk -> {
            assertThat(talk.id()).isEqualTo(talkId.toString());
            assertThat(talk.description()).isEqualTo("Про Loom");
        });
        assertThat(inputs.pastEditions()).singleElement().extracting(EventAudienceInputs.PastEdition::company).isEqualTo("Яндекс");
    }

    @Test
    void noParsingLookupForNamelessTerm() {
        EventsRecord event = AudienceTestData.event(UUID.randomUUID(), 7L, "published");
        event.setName("2026");
        when(eventRepository.findApplicationsByEvent(eq(event.getId()), anyString())).thenReturn(List.of());
        when(talkRepository.findByIds(anyList())).thenReturn(List.of());

        assertThat(builder.build(event).pastEditions()).isEmpty();
        verify(parsingRepository, never()).findByConferenceContaining(anyString(), anyInt());
    }
}
