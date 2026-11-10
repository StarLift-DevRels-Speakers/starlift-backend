package ru.shaxowskiy.javaspeakerclub.audience.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ожидаемая аудитория мероприятия: читают все, правит и пересчитывает организатор.
 */
@RestController
@RequestMapping("/api/events/{eventId}/audience-profile")
@RequiredArgsConstructor
public class EventAudienceProfileController {

    private final EventAudienceProfileService service;

    @GetMapping
    public EventAudienceProfileResponse get(@PathVariable UUID eventId) {
        return service.get(eventId);
    }

    @PutMapping
    public EventAudienceProfileResponse update(@PathVariable UUID eventId, @RequestBody EventAudienceProfileUpdate update) {
        return service.update(eventId, update);
    }

    @PostMapping("/refresh")
    public EventAudienceProfileResponse refresh(@PathVariable UUID eventId) {
        return service.refresh(eventId);
    }
}
