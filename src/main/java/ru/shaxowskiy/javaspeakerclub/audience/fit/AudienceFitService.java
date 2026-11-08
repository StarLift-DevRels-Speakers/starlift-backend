package ru.shaxowskiy.javaspeakerclub.audience.fit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.material.Fragment;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialAssembler;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialExtractor;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitCheckRepository.Completion;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitModels.AudienceFitCheckResponse;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitModels.AudienceFitRequest;
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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Проверка доклада или презентации на соответствие ожидаемой аудитории мероприятия.
 * Запрос создаёт запись pending, сама проверка (разбор файла, профиль аудитории, LLM, валидация) идёт в фоне.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudienceFitService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_INSUFFICIENT_DATA = "insufficient_data";
    public static final String STATUS_FAILED = "failed";

    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_UPLOAD = "upload";
    public static final String TRIGGER_APPLICATION = "application";

    static final List<String> ACTIVE_APPLICATION_STATUSES = List.of("sent", "reserve", "accepted");
    static final String STATE_PUBLISHED = "published";

    private static final String PENDING_MESSAGE = "Проверяем материал под аудиторию мероприятия…";
    private static final String FAILED_MESSAGE = "Не удалось проверить материал. Попробуйте ещё раз позже.";
    private static final String STALE_MESSAGE = "Проверка прервалась. Запустите её ещё раз.";
    private static final String PROFILE_FAILED = "Не удалось определить аудиторию мероприятия.";
    private static final String UNSUPPORTED_FORMAT = "Поддерживаются файлы PDF, PPTX, DOCX, TXT и MD.";
    private static final int SPEAKER_LIST_LIMIT = 50;
    private static final int EVENT_LIST_LIMIT = 100;
    private static final Map<String, String> VERDICT_LABELS = Map.of(
            AudienceFitValidator.FITS, "подходит",
            AudienceFitValidator.PARTIALLY, "подходит частично",
            AudienceFitValidator.NOT_FITS, "не подходит"
    );

    private final AudienceFitCheckRepository checkRepository;
    private final EventRepository eventRepository;
    private final SpeakerTalkRepository speakerTalkRepository;
    private final UserMediaRepository userMediaRepository;
    private final MinioService minioService;
    private final MaterialExtractor extractor;
    private final MaterialAssembler assembler;
    private final EventAudienceProfileService profileService;
    private final AudienceFitValidator validator;
    private final AudienceFitPrompt prompt;
    private final ProducerLlmClient llmClient;
    private final ProducerJson json;
    private final NotificationService notificationService;
    private final SpeakerAccessGuard accessGuard;
    private final ApplicationEventPublisher events;
    private final AudienceProperties properties;

    /**
     * Ручной запуск: спикер проверяет свой доклад и/или файл под опубликованное мероприятие.
     */
    public AudienceFitCheckResponse request(AudienceFitRequest request) {
        Long me = accessGuard.currentUserId();
        if (request == null || request.eventId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите мероприятие");
        }
        EventsRecord event = eventRepository.findEvent(request.eventId())
                .filter(item -> STATE_PUBLISHED.equals(item.getState()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Мероприятие не найдено"));

        UserMediaRecord media = null;
        if (request.mediaId() != null) {
            media = userMediaRepository.findByIdAndUserId(request.mediaId(), me)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден"));
            if (!extractor.supports(fileName(media))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, UNSUPPORTED_FORMAT);
            }
        }
        UUID talkId = request.talkId() != null ? request.talkId() : media == null ? null : media.getSpeakerTalkId();
        if (talkId != null) {
            speakerTalkRepository.findById(talkId)
                    .filter(talk -> talk.getSpeakerId().equals(me))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Доклад не найден"));
        }
        if (talkId == null && media == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите доклад или файл");
        }

        AudienceFitChecksRecord check = checkRepository.insertPending(event.getId(), me, talkId, request.mediaId(), TRIGGER_MANUAL);
        events.publishEvent(new FitCheckRequested(check.getId()));
        return toResponses(List.of(check)).getFirst();
    }

    /**
     * Спикер видит свои проверки. Организатор, указав своё мероприятие, видит готовые проверки по нему.
     */
    public List<AudienceFitCheckResponse> find(UUID eventId, UUID talkId, UUID mediaId) {
        Long me = accessGuard.currentUserId();
        if (eventId != null && accessGuard.isDevrel() && ownsEvent(eventId, me)) {
            return toResponses(checkRepository.findReadyByEvent(eventId, EVENT_LIST_LIMIT));
        }
        return toResponses(checkRepository.findBySpeaker(me, eventId, talkId, mediaId, SPEAKER_LIST_LIMIT));
    }

    public AudienceFitCheckResponse get(UUID id) {
        Long me = accessGuard.currentUserId();
        AudienceFitChecksRecord check = checkRepository.findById(id)
                .filter(item -> item.getSpeakerId().equals(me) || (accessGuard.isDevrel() && ownsEvent(item.getEventId(), me)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Проверка не найдена"));
        return toResponses(List.of(check)).getFirst();
    }

    /**
     * Новый файл доклада проверяется под каждое предстоящее мероприятие, куда доклад подан.
     */
    public void checkUploadedMedia(Long speakerId, UUID mediaId, UUID talkId) {
        Optional<UserMediaRecord> media = userMediaRepository.findById(mediaId);
        if (media.isEmpty() || !extractor.supports(fileName(media.get()))) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<UUID> eventIds = eventRepository.findApplicationsByTalk(speakerId, talkId, ACTIVE_APPLICATION_STATUSES).stream()
                .map(EventApplicationsRecord::getEventId)
                .distinct()
                .toList();
        for (EventsRecord event : eventRepository.findByIds(eventIds)) {
            if (event.getEventDate() != null && event.getEventDate().isBefore(today)) {
                continue;
            }
            AudienceFitChecksRecord check = checkRepository.insertPending(event.getId(), speakerId, talkId, mediaId, TRIGGER_UPLOAD);
            run(check.getId());
        }
    }

    public void checkApplication(Long speakerId, UUID eventId, UUID talkId) {
        AudienceFitChecksRecord check = checkRepository.insertPending(eventId, speakerId, talkId, null, TRIGGER_APPLICATION);
        run(check.getId());
    }

    /**
     * Сама проверка. Вызывается в фоне; любая ошибка сохраняется в записи со статусом failed.
     */
    public void run(UUID checkId) {
        AudienceFitChecksRecord check = checkRepository.findById(checkId).orElse(null);
        if (check == null || !STATUS_PENDING.equals(check.getStatus())) {
            return;
        }
        UUID mediaId = check.getMediaId();
        try {
            EventsRecord event = eventRepository.findEvent(check.getEventId()).orElseThrow();
            SpeakerTalksRecord talk = check.getSpeakerTalkId() == null
                    ? null
                    : speakerTalkRepository.findById(check.getSpeakerTalkId()).orElse(null);
            UserMediaRecord media = mediaId != null
                    ? userMediaRepository.findById(mediaId).orElse(null)
                    : latestSupportedFile(talk);
            mediaId = media == null ? null : media.getId();

            MaterialSnapshot material = material(talk, media);
            if (material.totalChars() < properties.minMaterialChars()) {
                complete(check, new Completion(mediaId, STATUS_INSUFFICIENT_DATA, null, json.write(material), null, null,
                        null, null, insufficientMessage(material)));
                return;
            }
            String contentHash = assembler.contentHash(material);

            EventAudienceProfilesRecord profileRecord = profileService.ensure(event);
            EventAudienceProfile profile = profileService.readProfile(profileRecord);
            if (profile == null) {
                complete(check, new Completion(mediaId, STATUS_FAILED, contentHash, json.write(material), null, null,
                        null, prompt.version(), PROFILE_FAILED));
                return;
            }

            Optional<AudienceFitChecksRecord> cached = checkRepository.findReadyByContent(event.getId(), contentHash, profileRecord.getUpdatedDate());
            if (cached.isPresent()) {
                AudienceFitChecksRecord source = cached.get();
                complete(check, new Completion(mediaId, STATUS_READY, contentHash, source.getMaterialJson(), source.getResultJson(),
                        source.getValidationJson(), source.getModel(), source.getPromptVersion(), null));
                notifyResult(check, event, talk, media, json.read(source.getResultJson(), AudienceFitResult.class));
                return;
            }

            AudienceFitDraft draft = llmClient.generate(
                    prompt.system(),
                    prompt.user(event.getName(), json.write(profile), json.write(material.fragments())),
                    AudienceFitDraft.class,
                    properties.maxResponseTokens()
            );
            AudienceFitValidator.Result result = validator.validate(draft, material, profile.confidence());
            complete(check, new Completion(mediaId, STATUS_READY, contentHash, json.write(material), json.write(result.result()),
                    json.write(result.rejections()), llmClient.modelName(), prompt.version(), null));
            notifyResult(check, event, talk, media, result.result());
        } catch (RuntimeException e) {
            log.warn("Audience fit check {} failed: {}", checkId, e.getMessage());
            complete(check, new Completion(mediaId, STATUS_FAILED, null, null, null, null, llmClient.modelName(),
                    prompt.version(), e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private MaterialSnapshot material(SpeakerTalksRecord talk, UserMediaRecord media) {
        if (media == null) {
            return assembler.assemble(talk, null, List.of(), false);
        }
        String name = fileName(media);
        if (!extractor.supports(name)) {
            return assembler.assemble(talk, name, List.of(), true);
        }
        List<Fragment> fragments;
        try (InputStream in = minioService.getFile(media.getMediaMinioKey())) {
            fragments = extractor.extract(name, in);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл " + name, e);
        }
        return assembler.assemble(talk, name, fragments, false);
    }

    private UserMediaRecord latestSupportedFile(SpeakerTalksRecord talk) {
        if (talk == null) {
            return null;
        }
        return userMediaRepository.findByTalkId(talk.getId()).stream()
                .filter(file -> extractor.supports(fileName(file)))
                .findFirst()
                .orElse(null);
    }

    private static String insufficientMessage(MaterialSnapshot material) {
        if (material.unsupportedFile() != null) {
            return "Не удалось прочитать «" + material.unsupportedFile() + "». " + UNSUPPORTED_FORMAT
                    + " Или добавьте подробное описание доклада.";
        }
        if (material.fileName() != null) {
            return "В файле «" + material.fileName() + "» почти нет текста (возможно, слайды — картинки). "
                    + "Добавьте подробное описание доклада или загрузите версию с текстом.";
        }
        return "Мало материала для проверки: добавьте подробное описание доклада или загрузите презентацию.";
    }

    private void complete(AudienceFitChecksRecord check, Completion completion) {
        checkRepository.complete(check.getId(), completion);
    }

    /**
     * Об автоматической проверке спикер узнаёт из уведомления; ручную он ждёт на экране.
     */
    private void notifyResult(AudienceFitChecksRecord check, EventsRecord event, SpeakerTalksRecord talk,
                              UserMediaRecord media, AudienceFitResult result) {
        if (TRIGGER_MANUAL.equals(check.getTriggerSource())) {
            return;
        }
        String subject = media != null ? "Презентация «" + fileName(media) + "»"
                : talk != null ? "Доклад «" + talk.getName() + "»"
                : "Материал";
        notificationService.notify(check.getSpeakerId(), "%s для «%s»: %s (%d/100)"
                .formatted(subject, event.getName(), VERDICT_LABELS.get(result.verdict()), result.score()));
    }

    private boolean ownsEvent(UUID eventId, Long userId) {
        return eventRepository.findEvent(eventId).map(event -> event.getOwnerId().equals(userId)).orElse(false);
    }

    List<AudienceFitCheckResponse> toResponses(List<AudienceFitChecksRecord> checks) {
        Map<UUID, EventsRecord> eventsById = eventRepository.findByIds(ids(checks, AudienceFitChecksRecord::getEventId)).stream()
                .collect(Collectors.toMap(EventsRecord::getId, Function.identity()));
        Map<UUID, SpeakerTalksRecord> talks = speakerTalkRepository.findByIds(ids(checks, AudienceFitChecksRecord::getSpeakerTalkId)).stream()
                .collect(Collectors.toMap(SpeakerTalksRecord::getId, Function.identity()));
        Map<UUID, UserMediaRecord> files = userMediaRepository.findByIds(ids(checks, AudienceFitChecksRecord::getMediaId)).stream()
                .collect(Collectors.toMap(UserMediaRecord::getId, Function.identity()));
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(properties.pendingTimeoutMinutes());

        return checks.stream().map(check -> {
            String status = check.getStatus();
            boolean stale = STATUS_PENDING.equals(status) && check.getCreatedDate() != null && check.getCreatedDate().isBefore(staleBefore);
            if (stale) {
                status = STATUS_FAILED;
            }
            String message = switch (status) {
                case STATUS_PENDING -> PENDING_MESSAGE;
                case STATUS_INSUFFICIENT_DATA -> check.getError();
                case STATUS_FAILED -> stale ? STALE_MESSAGE
                        : PROFILE_FAILED.equals(check.getError()) ? PROFILE_FAILED : FAILED_MESSAGE;
                default -> null;
            };
            EventsRecord event = eventsById.get(check.getEventId());
            SpeakerTalksRecord talk = check.getSpeakerTalkId() == null ? null : talks.get(check.getSpeakerTalkId());
            UserMediaRecord file = check.getMediaId() == null ? null : files.get(check.getMediaId());
            return new AudienceFitCheckResponse(
                    check.getId(),
                    check.getEventId(),
                    event == null ? null : event.getName(),
                    check.getSpeakerId(),
                    check.getSpeakerTalkId(),
                    talk == null ? null : talk.getName(),
                    check.getMediaId(),
                    file == null ? null : fileName(file),
                    check.getTriggerSource(),
                    status,
                    message,
                    STATUS_READY.equals(status) && check.getResultJson() != null
                            ? json.read(check.getResultJson(), AudienceFitResult.class)
                            : null,
                    check.getCreatedDate(),
                    check.getUpdatedDate()
            );
        }).toList();
    }

    private static List<UUID> ids(List<AudienceFitChecksRecord> checks, Function<AudienceFitChecksRecord, UUID> getter) {
        return checks.stream().map(getter).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Имя файла: сохранённое при загрузке или из ключа объекта folder/uuid_имя для старых файлов.
     */
    static String fileName(UserMediaRecord media) {
        if (media.getOriginalName() != null && !media.getOriginalName().isBlank()) {
            return media.getOriginalName();
        }
        String key = media.getMediaMinioKey();
        String last = key.substring(key.lastIndexOf('/') + 1);
        int separator = last.indexOf('_');
        return separator >= 0 ? last.substring(separator + 1) : last;
    }
}
