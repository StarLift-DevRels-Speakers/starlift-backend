package ru.shaxowskiy.javaspeakerclub.audience.profile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfileRepository.SaveProfile;
import ru.shaxowskiy.javaspeakerclub.events.EventRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventAudienceProfilesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.util.Optional;
import java.util.UUID;

/**
 * Профиль ожидаемой аудитории мероприятия. Считается по данным мероприятия, хранится вместе с хешем входных данных
 * и пересчитывается, только когда данные изменились. Правка организатора не перезаписывается.
 * Вызов LLM идёт вне транзакции, результат сохраняется отдельной записью.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAudienceProfileService {

    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";
    static final String STATE_PUBLISHED = "published";

    private static final String FAILED_MESSAGE = "Не удалось определить аудиторию мероприятия. Попробуйте позже.";

    private final EventRepository eventRepository;
    private final EventAudienceInputsBuilder inputsBuilder;
    private final EventAudienceProfileValidator validator;
    private final EventAudienceProfileRepository repository;
    private final EventAudiencePrompt prompt;
    private final ProducerLlmClient llmClient;
    private final ProducerJson json;
    private final AudienceProperties properties;
    private final SpeakerAccessGuard accessGuard;

    /**
     * Опубликованное мероприятие видят все, черновик — только его организатор.
     */
    public EventAudienceProfileResponse get(UUID eventId) {
        EventsRecord event = eventRepository.findEvent(eventId)
                .filter(item -> STATE_PUBLISHED.equals(item.getState()) || item.getOwnerId().equals(accessGuard.currentUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Мероприятие не найдено"));
        return toResponse(ensure(event));
    }

    public EventAudienceProfileResponse update(UUID eventId, EventAudienceProfileUpdate update) {
        EventsRecord event = requireOwned(eventId);
        if (update == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пустой профиль");
        }
        EventAudienceProfile profile = validator.manual(update);
        return toResponse(repository.save(new SaveProfile(
                event.getId(), STATUS_READY, json.write(profile), null, null, null, true, null, null, null
        )));
    }

    /**
     * Сбрасывает правку организатора и заново выводит профиль из данных мероприятия.
     */
    public EventAudienceProfileResponse refresh(UUID eventId) {
        EventsRecord event = requireOwned(eventId);
        return toResponse(compute(event, inputsBuilder.build(event)));
    }

    /**
     * Актуальный профиль: сохранённый, если данные мероприятия не менялись или профиль правил организатор, иначе новый.
     */
    public EventAudienceProfilesRecord ensure(EventsRecord event) {
        EventAudienceInputs inputs = inputsBuilder.build(event);
        Optional<EventAudienceProfilesRecord> existing = repository.findByEventId(event.getId());
        if (existing.isPresent()) {
            EventAudienceProfilesRecord record = existing.get();
            boolean fresh = STATUS_READY.equals(record.getStatus()) && inputsHash(inputs).equals(record.getInputsHash());
            if (Boolean.TRUE.equals(record.getManual()) || fresh) {
                return record;
            }
        }
        return compute(event, inputs);
    }

    /**
     * Фоновый пересчёт после публикации или правки мероприятия, чтобы спикер не ждал LLM.
     */
    public void warmUp(UUID eventId) {
        eventRepository.findEvent(eventId)
                .filter(event -> STATE_PUBLISHED.equals(event.getState()))
                .ifPresent(event -> {
                    try {
                        ensure(event);
                    } catch (RuntimeException e) {
                        log.warn("Audience profile warm-up failed for eventId={}: {}", eventId, e.getMessage());
                    }
                });
    }

    public EventAudienceProfile readProfile(EventAudienceProfilesRecord record) {
        return record.getProfileJson() == null ? null : json.read(record.getProfileJson(), EventAudienceProfile.class);
    }

    private EventAudienceProfilesRecord compute(EventsRecord event, EventAudienceInputs inputs) {
        String inputsJson = json.write(inputs);
        EventAudienceProfileDraft draft;
        try {
            draft = llmClient.generate(prompt.system(), prompt.user(inputsJson), EventAudienceProfileDraft.class, properties.maxResponseTokens());
        } catch (RuntimeException e) {
            log.warn("Audience profile generation failed for eventId={}: {}", event.getId(), e.getMessage());
            return repository.save(new SaveProfile(
                    event.getId(), STATUS_FAILED, null, inputsJson, null, null, false,
                    llmClient.modelName(), prompt.version(), e.getClass().getSimpleName() + ": " + e.getMessage()
            ));
        }
        EventAudienceProfileValidator.Result result = validator.validate(draft, inputs);
        return repository.save(new SaveProfile(
                event.getId(),
                STATUS_READY,
                json.write(result.profile()),
                inputsJson,
                inputsHash(inputs),
                json.write(result.rejections()),
                false,
                llmClient.modelName(),
                prompt.version(),
                null
        ));
    }

    /**
     * Хеш входных данных вместе с версией промпта: новая версия промпта тоже пересчитывает профиль.
     */
    String inputsHash(EventAudienceInputs inputs) {
        return Texts.sha256(prompt.version() + "\n" + json.write(inputs));
    }

    private EventsRecord requireOwned(UUID eventId) {
        Long me = accessGuard.currentUserId();
        return eventRepository.findEvent(eventId)
                .filter(event -> event.getOwnerId().equals(me))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к мероприятию"));
    }

    EventAudienceProfileResponse toResponse(EventAudienceProfilesRecord record) {
        boolean failed = STATUS_FAILED.equals(record.getStatus());
        return new EventAudienceProfileResponse(
                record.getEventId(),
                record.getStatus(),
                failed ? FAILED_MESSAGE : null,
                readProfile(record),
                Boolean.TRUE.equals(record.getManual()),
                record.getModel(),
                record.getPromptVersion(),
                record.getUpdatedDate()
        );
    }
}
