package ru.shaxowskiy.javaspeakerclub.events;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.ApplicationCreateRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.ApplicationResponse;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.DecisionRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.EventRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.EventResponse;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.InvitationAnswerRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.InvitationCreateRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.InvitationResponse;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.TalkChangeAnswerRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.TalkChangeRequest;

import java.util.List;
import java.util.UUID;

/**
 * Мероприятия, заявки на CFP и приглашения. Права по ролям — в SecurityConfig, владение — в сервисах.
 */
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final ApplicationService applicationService;
    private final InvitationService invitationService;

    @GetMapping("/api/events")
    public List<EventResponse> events() {
        return eventService.findVisible();
    }

    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@RequestBody EventRequest request) {
        return eventService.create(request);
    }

    @PutMapping("/api/events/{id}")
    public EventResponse updateEvent(@PathVariable UUID id, @RequestBody EventRequest request) {
        return eventService.update(id, request);
    }

    @PostMapping("/api/events/{id}/publish")
    public EventResponse publishEvent(@PathVariable UUID id) {
        return eventService.publish(id);
    }

    @GetMapping("/api/applications")
    public List<ApplicationResponse> applications() {
        return applicationService.findVisible();
    }

    @PostMapping("/api/applications")
    public ResponseEntity<Void> submitApplication(@RequestBody ApplicationCreateRequest request) {
        applicationService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/api/applications/{id}/talk-change")
    public ResponseEntity<Void> requestTalkChange(@PathVariable UUID id, @RequestBody TalkChangeRequest request) {
        applicationService.requestTalkChange(id, request == null ? null : request.talkId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/applications/{id}/talk-change/answer")
    public ResponseEntity<Void> answerTalkChange(@PathVariable UUID id, @RequestBody TalkChangeAnswerRequest request) {
        applicationService.answerTalkChange(id, request != null && request.approved());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/applications/{id}/decision")
    public ResponseEntity<Void> decide(@PathVariable UUID id, @RequestBody DecisionRequest request) {
        applicationService.decide(id, request == null ? null : request.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/invitations")
    public List<InvitationResponse> invitations() {
        return invitationService.findVisible();
    }

    @PostMapping("/api/invitations")
    public ResponseEntity<Void> sendInvitation(@RequestBody InvitationCreateRequest request) {
        invitationService.send(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/api/invitations/{id}/answer")
    public ResponseEntity<Void> answerInvitation(@PathVariable UUID id, @RequestBody InvitationAnswerRequest request) {
        invitationService.answer(id, request == null ? null : request.status());
        return ResponseEntity.noContent().build();
    }
}
