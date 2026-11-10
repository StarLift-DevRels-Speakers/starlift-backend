package ru.shaxowskiy.javaspeakerclub.audience.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.AcceptedTalk;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.EventInfo;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.PastEdition;
import ru.shaxowskiy.javaspeakerclub.events.EventRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventApplicationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerTalksRecord;
import ru.shaxowskiy.javaspeakerclub.repository.ParsingEventRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerTalkRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Собирает входные данные профиля аудитории из БД.
 */
@Component
@RequiredArgsConstructor
public class EventAudienceInputsBuilder {

    static final String STATUS_ACCEPTED = "accepted";
    private static final int MAX_TALK_DESCRIPTION = 400;
    private static final int MIN_CONFERENCE_TERM = 3;

    private final EventRepository eventRepository;
    private final SpeakerTalkRepository speakerTalkRepository;
    private final ParsingEventRepository parsingEventRepository;
    private final AudienceProperties properties;

    public EventAudienceInputs build(EventsRecord event) {
        EventInfo info = new EventInfo(
                event.getName(),
                event.getEventDate(),
                event.getCity(),
                event.getFormat(),
                event.getTopic(),
                Texts.trimToNull(event.getAudience()),
                Texts.trimToNull(event.getDescription())
        );
        return new EventAudienceInputs(info, acceptedTalks(event.getId()), pastEditions(event.getName()));
    }

    private List<AcceptedTalk> acceptedTalks(UUID eventId) {
        List<UUID> talkIds = eventRepository.findApplicationsByEvent(eventId, STATUS_ACCEPTED).stream()
                .map(EventApplicationsRecord::getSpeakerTalkId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(properties.acceptedTalksLimit())
                .toList();
        Map<UUID, SpeakerTalksRecord> talks = speakerTalkRepository.findByIds(talkIds).stream()
                .collect(Collectors.toMap(SpeakerTalksRecord::getId, Function.identity()));
        return talkIds.stream()
                .map(talks::get)
                .filter(Objects::nonNull)
                .map(talk -> new AcceptedTalk(
                        talk.getId().toString(),
                        talk.getName(),
                        talk.getTopic(),
                        Texts.truncate(Texts.trimToNull(talk.getDescription()), MAX_TALK_DESCRIPTION)
                ))
                .toList();
    }

    private List<PastEdition> pastEditions(String eventName) {
        String term = conferenceTerm(eventName);
        if (term == null) {
            return List.of();
        }
        return parsingEventRepository.findByConferenceContaining(term, properties.pastEditionsLimit()).stream()
                .map(row -> new PastEdition(row.getId().toString(), row.getConference(), row.getSpeakerName(), row.getCompany(), row.getEventDate()))
                .toList();
    }

    /**
     * Название без года и знаков: «JPoint 2026» → «jpoint», чтобы найти прошлые выпуски.
     */
    static String conferenceTerm(String eventName) {
        if (eventName == null) {
            return null;
        }
        String term = eventName.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(19|20)\\d{2}\\b", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return term.length() < MIN_CONFERENCE_TERM ? null : term;
    }
}
