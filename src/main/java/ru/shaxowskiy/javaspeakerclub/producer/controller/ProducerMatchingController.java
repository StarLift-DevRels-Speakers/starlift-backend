package ru.shaxowskiy.javaspeakerclub.producer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.ExpertApi;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.MetricsView;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.OrganizerApi;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventRecommendationResult;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.SpeakerMatchResult;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.EventCfpPublisher;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.EventRequestService;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.EventSpeakerSelectionService;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.ExpertRecommendationService;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.ProducerActor;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.ProducerInteractionService;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.ProducerMetricsService;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.util.List;
import java.util.UUID;

/**
 * Подбор по ТЗ AI-продюсера. /events — организатор (DEVREL, ADMIN), /me — эксперт, /metrics — DEVREL, ADMIN.
 * Роли проверяет SecurityConfig, владельца мероприятия — сервисы.
 */
@RestController
@RequestMapping("/api/producer")
@RequiredArgsConstructor
public class ProducerMatchingController {

    private final EventRequestService eventService;
    private final EventSpeakerSelectionService selectionService;
    private final EventCfpPublisher cfpPublisher;
    private final ExpertRecommendationService expertService;
    private final ProducerInteractionService interactionService;
    private final ProducerMetricsService metricsService;
    private final SpeakerAccessGuard accessGuard;

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventCard createEvent(@RequestBody OrganizerApi.EventCreateRequest request) {
        return eventService.create(actor(), request);
    }

    @GetMapping("/events")
    public List<OrganizerApi.EventListItem> listEvents() {
        return eventService.list(actor());
    }

    @GetMapping("/events/{id}")
    public EventCard getEvent(@PathVariable UUID id) {
        return eventService.get(actor(), id);
    }

    @PatchMapping("/events/{id}")
    public EventCard updateEvent(@PathVariable UUID id, @RequestBody OrganizerApi.EventUpdateRequest request) {
        return eventService.update(actor(), id, request);
    }

    @PostMapping("/events/{id}/match")
    public SpeakerMatchResult matchSpeakers(@PathVariable UUID id) {
        return selectionService.match(actor(), id);
    }

    @GetMapping("/events/{id}/match")
    public SpeakerMatchResult latestMatch(@PathVariable UUID id) {
        return selectionService.latest(actor(), id);
    }

    @PostMapping("/events/{id}/candidates/{expertId}/actions")
    public OrganizerApi.CandidateActionResponse candidateAction(
            @PathVariable UUID id,
            @PathVariable Long expertId,
            @RequestBody OrganizerApi.CandidateActionRequest request
    ) {
        return interactionService.candidateAction(actor(), id, expertId, request.action(), request.note());
    }

    @PostMapping("/events/{id}/cfp")
    public OrganizerApi.CfpPublishResponse publishCfp(@PathVariable UUID id, @RequestBody OrganizerApi.CfpPublishRequest request) {
        return cfpPublisher.publish(actor(), id, request);
    }

    @GetMapping("/events/{id}/history")
    public List<OrganizerApi.HistoryItem> history(@PathVariable UUID id) {
        return interactionService.history(actor(), id);
    }

    @GetMapping("/me/profile")
    public ExpertProfile myProfile() {
        return expertService.profile(accessGuard.currentUserId());
    }

    @PutMapping("/me/preferences")
    public ExpertProfile savePreferences(@RequestBody ExpertApi.PreferencesRequest request) {
        return expertService.savePreferences(accessGuard.currentUserId(), request);
    }

    @PostMapping("/me/events")
    public EventRecommendationResult recommendEvents() {
        return expertService.recommend(accessGuard.currentUserId());
    }

    @PostMapping("/me/events/{cfpId}/actions")
    public ExpertApi.ActionResponse eventAction(@PathVariable UUID cfpId, @RequestBody ExpertApi.EventActionRequest request) {
        return interactionService.expertEventAction(accessGuard.currentUserId(), cfpId, request.action(), request.note());
    }

    @GetMapping("/me/invitations")
    public List<ExpertApi.InvitationView> myInvitations() {
        return interactionService.invitations(accessGuard.currentUserId());
    }

    @PostMapping("/me/invitations/{eventId}/response")
    public ExpertApi.InvitationView respond(@PathVariable UUID eventId, @RequestBody ExpertApi.InvitationResponseRequest request) {
        return interactionService.respond(accessGuard.currentUserId(), eventId, request.response(), request.note());
    }

    @GetMapping("/metrics")
    public MetricsView metrics() {
        return metricsService.metrics();
    }

    private ProducerActor actor() {
        return new ProducerActor(accessGuard.currentUserId(), accessGuard.isAdmin());
    }
}
