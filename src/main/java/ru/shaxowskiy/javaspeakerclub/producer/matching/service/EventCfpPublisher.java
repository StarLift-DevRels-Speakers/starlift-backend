package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.CfpRecommendationsRecord;
import ru.shaxowskiy.javaspeakerclub.producer.matching.analysis.EventCardAssembler;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.CfpPublishRequest;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.CfpPublishResponse;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository.EventCfpInput;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Организатор открывает CFP своего мероприятия: оно попадает в общий список мероприятий для подбора спикерам.
 * Мероприятие и CFP — одна сущность для спикера: поля CFP берутся из карточки, а не вводятся заново.
 * В CFP переносятся только известные параметры карточки, а обязательные требования — только те, что указаны явно.
 */
@Service
@RequiredArgsConstructor
public class EventCfpPublisher {

    private static final List<String> LEVELS = List.of("local", "regional", "federal", "international");
    private static final int MAX_DESCRIPTION = 1000;

    private final EventRequestService eventService;
    private final CfpRecommendationRepository cfpRepository;
    private final UserRepository userRepository;

    @Transactional
    public CfpPublishResponse publish(ProducerActor actor, UUID eventId, CfpPublishRequest request) {
        if (request.deadline() == null || request.deadline().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите дедлайн CFP не раньше сегодняшнего дня");
        }
        String level = request.level() == null ? "local" : request.level().trim().toLowerCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "level должен быть одним из " + LEVELS);
        }
        EventRequestService.Loaded loaded = eventService.load(actor, eventId);
        EventCard card = loaded.card();
        List<String> topics = card.values(EventParamKey.TOPICS);
        if (topics.isEmpty() && card.tags().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не указана тематика мероприятия: спикерам нечего с ней сопоставить. Ответьте на вопрос о темах.");
        }
        String host = userRepository.findById(loaded.record().getOrganizerId())
                .map(user -> user.getCompany() == null || "Unknown".equals(user.getCompany()) ? "StarLift" : user.getCompany())
                .orElse("StarLift");
        String attendance = card.value(EventParamKey.ATTENDANCE);
        String city = card.value(EventParamKey.CITY);
        if (city == null && !"online".equals(attendance)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Не указан город мероприятия: спикеры не поймут, куда ехать. Ответьте на вопрос о городе.");
        }
        CfpRecommendationsRecord cfp = cfpRepository.upsertForEvent(new EventCfpInput(
                eventId,
                EventRequestService.displayTitle(card),
                city != null ? city : "Онлайн",
                level,
                topics.isEmpty() ? String.join(", ", card.tags()) : String.join(", ", topics),
                request.deadline(),
                host,
                request.url() == null ? "" : request.url().trim(),
                description(card),
                talkFormat(card),
                card.value(EventParamKey.LANGUAGE),
                card.value(EventParamKey.AUDIENCE_LEVEL),
                attendance,
                duration(card),
                requirements(card),
                card.tags()));
        List<String> requirements = cfp.getRequirements() == null ? List.of()
                : List.of(cfp.getRequirements().split(";")).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
        return new CfpPublishResponse(cfp.getId().toString(), cfp.getTitle(), cfp.getDeadline(), requirements, card.tags());
    }

    private static String description(EventCard card) {
        if (card.description() == null || card.description().isBlank()) {
            return null;
        }
        String text = card.description().trim();
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION - 1) + "…" : text;
    }

    private static String talkFormat(EventCard card) {
        String type = card.value(EventParamKey.TALK_TYPE);
        if (type == null) {
            return null;
        }
        return EventParamKey.TALK_TYPE.options().getOrDefault(type, type);
    }

    private static Integer duration(EventCard card) {
        String minutes = card.value(EventParamKey.TALK_DURATION);
        return minutes == null ? null : Integer.valueOf(minutes);
    }

    /** Обязательные требования, заданные организатором или явно сказанные в описании; интерпретации ИИ не переносятся. */
    private static String requirements(EventCard card) {
        List<String> items = new ArrayList<>();
        card.criteria().stream()
                .filter(criterion -> EventCardAssembler.MANDATORY.equals(criterion.kind()))
                .filter(criterion -> !criterion.interpretation())
                .filter(criterion -> List.of("competency", "industry", "topic", "format", "other").contains(criterion.check()))
                .map(EventCard.Criterion::text)
                .forEach(items::add);
        return items.isEmpty() ? null : items.stream().distinct().collect(Collectors.joining("; "));
    }
}
