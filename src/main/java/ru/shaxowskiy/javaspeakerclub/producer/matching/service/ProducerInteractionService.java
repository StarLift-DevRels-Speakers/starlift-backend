package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.CfpRecommendationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerInteractionsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.ExpertApi.ActionResponse;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.ExpertApi.InvitationView;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.CandidateActionResponse;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi.HistoryItem;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Interactions;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.SpeakerMatchResult.Candidate;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository.NewInteraction;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Факты истории взаимодействий (US-08, US-09, US-18): что сделали организатор и эксперт после рекомендации.
 * Каждое действие проверяется: кто его может сделать и в каком порядке. Отказ спикера сохраняется как факт,
 * но в подборе не считается негативом (п. 24 ТЗ).
 */
@Service
@RequiredArgsConstructor
public class ProducerInteractionService {

    private static final int HISTORY_LIMIT = 200;

    private final EventRequestService eventService;
    private final EventSpeakerSelectionService selectionService;
    private final ProducerInteractionRepository interactionRepository;
    private final CfpRecommendationRepository cfpRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public CandidateActionResponse candidateAction(ProducerActor actor, UUID eventId, Long expertId, String action, String note) {
        String normalized = normalize(action);
        if (!Interactions.ORGANIZER_ACTIONS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action должен быть одним из " + Interactions.ORGANIZER_ACTIONS);
        }
        EventRequestService.Loaded loaded = eventService.load(actor, eventId);
        UsersRecord expert = requireExpert(expertId);
        List<ProducerInteractionsRecord> facts = interactionRepository.findFacts(expertId, eventId);
        String status = invitationStatus(facts);
        if (Interactions.TALK_GIVEN.equals(normalized) && !"accepted".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Выступление можно отметить только после того, как спикер принял приглашение");
        }
        if (Interactions.INVITED.equals(normalized) && status != null && !"declined".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Приглашение уже отправлено, статус: "
                    + Interactions.invitationStatusLabel(status).toLowerCase());
        }

        EventCard card = loaded.card();
        String topic = card.value(EventParamKey.TOPICS);
        String format = card.value(EventParamKey.EVENT_FORMAT);
        Long organizerId = loaded.record().getOrganizerId();
        List<String> alsoRecorded = new ArrayList<>();
        // Повторное приглашение — положительный сигнал (п. 9 ТЗ): тот же организатор зовёт того, кто у него уже выступал.
        boolean repeat = Interactions.INVITED.equals(normalized)
                && interactionRepository.existsFact(expertId, organizerId, Interactions.TALK_GIVEN);
        ProducerInteractionsRecord saved = interactionRepository.insert(new NewInteraction(null, expertId, eventId, null,
                actor.userId(), normalized, Interactions.KIND_FACT, topic, format, trimToNull(note), null));
        if (repeat) {
            interactionRepository.insert(new NewInteraction(null, expertId, eventId, null, actor.userId(),
                    Interactions.REPEAT_INVITATION, Interactions.KIND_FACT, topic, format,
                    "Организатор уже приглашал эксперта, выступление состоялось", null));
            alsoRecorded.add(Interactions.label(Interactions.REPEAT_INVITATION));
        }
        String newStatus = Interactions.INVITED.equals(normalized) ? "sent" : status;
        return new CandidateActionResponse(displayName(expert), normalized, Interactions.label(normalized),
                newStatus == null ? null : Interactions.invitationStatusLabel(newStatus), alsoRecorded, saved.getCreatedDate());
    }

    public ActionResponse expertEventAction(Long userId, UUID cfpId, String action, String note) {
        String normalized = normalize(action);
        if (!Interactions.EXPERT_EVENT_ACTIONS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action должен быть одним из " + Interactions.EXPERT_EVENT_ACTIONS);
        }
        CfpRecommendationsRecord cfp = cfpRepository.findById(cfpId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CFP не найден: " + cfpId));
        ProducerInteractionsRecord saved = interactionRepository.insert(new NewInteraction(null, userId, cfp.getEventRequestId(),
                cfpId, userId, normalized, Interactions.KIND_FACT, cfp.getTopic(), cfp.getTalkFormat(), trimToNull(note), null));
        return new ActionResponse(normalized, Interactions.label(normalized), saved.getCreatedDate());
    }

    public List<InvitationView> invitations(Long userId) {
        Map<UUID, List<ProducerInteractionsRecord>> byEvent = interactionRepository
                .findEventFactsByExpert(userId, Interactions.INVITATION_ACTIONS).stream()
                .collect(Collectors.groupingBy(ProducerInteractionsRecord::getEventRequestId, LinkedHashMap::new, Collectors.toList()));
        return byEvent.entrySet().stream()
                .map(entry -> invitationView(userId, entry.getKey(), entry.getValue()))
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .toList();
    }

    public InvitationView respond(Long userId, UUID eventId, String response, String note) {
        String action = Interactions.INVITATION_RESPONSES.get(normalize(response));
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "response должен быть одним из " + Interactions.INVITATION_RESPONSES.keySet());
        }
        List<ProducerInteractionsRecord> facts = interactionRepository.findFacts(userId, eventId).stream()
                .filter(fact -> Interactions.INVITATION_ACTIONS.contains(fact.getAction()))
                .toList();
        if (facts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашения на это мероприятие нет");
        }
        ProducerInteractionsRecord invitation = facts.get(facts.size() - 1);
        interactionRepository.insert(new NewInteraction(null, userId, eventId, null, userId, action, Interactions.KIND_FACT,
                invitation.getTopic(), invitation.getFormat(), trimToNull(note), null));
        return invitationView(userId, eventId, interactionRepository.findFacts(userId, eventId).stream()
                .filter(fact -> Interactions.INVITATION_ACTIONS.contains(fact.getAction()))
                .toList());
    }

    public List<HistoryItem> history(ProducerActor actor, UUID eventId) {
        eventService.load(actor, eventId);
        List<ProducerInteractionsRecord> records = interactionRepository.find(null, eventId, null, null, HISTORY_LIMIT);
        List<Long> ids = records.stream().map(ProducerInteractionsRecord::getExpertId).filter(id -> id != null).distinct().toList();
        Map<Long, String> names = userRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(UsersRecord::getId, ProducerInteractionService::displayName));
        return records.stream()
                .map(record -> new HistoryItem(
                        Interactions.label(record.getAction()),
                        names.get(record.getExpertId()),
                        record.getKind(),
                        record.getAction(),
                        record.getConfidence(),
                        record.getNote(),
                        record.getCreatedDate(),
                        record.getExpertId()))
                .toList();
    }

    private InvitationView invitationView(Long userId, UUID eventId, List<ProducerInteractionsRecord> facts) {
        ProducerInteractionsRecord first = facts.get(0);
        ProducerInteractionsRecord last = facts.get(facts.size() - 1);
        String status = Interactions.invitationStatus(last.getAction());
        EventRequestService.Loaded loaded = eventService.loadForInvitation(eventId);
        EventCard card = loaded.card();
        Optional<Candidate> candidate = selectionService.latestRun(eventId)
                .flatMap(run -> run.response().groups().stream()
                        .flatMap(group -> group.items().stream())
                        .filter(item -> item.expertId() == userId)
                        .findFirst());
        String organizer = userRepository.findById(loaded.record().getOrganizerId()).map(ProducerInteractionService::displayName)
                .orElse(null);
        String note = facts.stream().filter(fact -> Interactions.INVITED.equals(fact.getAction()))
                .reduce((left, right) -> right).map(ProducerInteractionsRecord::getNote).orElse(null);
        return new InvitationView(
                EventRequestService.displayTitle(card),
                Interactions.invitationStatusLabel(status),
                candidate.map(Candidate::whyFits).orElse(List.of()),
                candidate.map(Candidate::aiSummary).orElse(null),
                eventLines(card),
                organizer,
                status,
                note,
                first.getCreatedDate(),
                last.getCreatedDate(),
                eventId.toString());
    }

    /** Коротко о мероприятии для спикера: только известные параметры, без догадок. */
    private static List<String> eventLines(EventCard card) {
        return Stream.of(EventParamKey.TOPICS, EventParamKey.DATE, EventParamKey.CITY, EventParamKey.ATTENDANCE,
                        EventParamKey.AUDIENCE_LEVEL, EventParamKey.TALK_TYPE, EventParamKey.TALK_DURATION, EventParamKey.LANGUAGE)
                .filter(card::known)
                .map(key -> key.label() + ": " + key.display(card.values(key)))
                .toList();
    }

    private UsersRecord requireExpert(Long expertId) {
        UsersRecord user = userRepository.findById(expertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Эксперт не найден: " + expertId));
        if (!userRoleRepository.findUserIdsByRole(AppRole.SPEAKER).contains(expertId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пользователь " + expertId + " не спикер");
        }
        return user;
    }

    /** null — приглашения не было; иначе статус по последнему действию в цепочке приглашения. */
    static String invitationStatus(List<ProducerInteractionsRecord> facts) {
        return facts.stream()
                .map(ProducerInteractionsRecord::getAction)
                .filter(Interactions.INVITATION_ACTIONS::contains)
                .reduce((left, right) -> right)
                .map(Interactions::invitationStatus)
                .orElse(null);
    }

    static String displayName(UsersRecord user) {
        String name = Stream.of(user.getFirstName(), user.getLastName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return name.isBlank() ? user.getUsername() : name;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
