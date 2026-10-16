package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.ProfilePatchRequest;
import ru.shaxowskiy.javaspeakerclub.entity.User;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.service.UserService;

import java.net.URI;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private static final String DEFAULT_CITY = "Не указан";

    private final UserService userService;
    private final TalkRepository talkRepository;

    public record TalkDto(
            String id,
            String topic,
            java.time.LocalDateTime talkDate,
            String conferenceType,
            String format,
            String activityName,
            java.time.LocalDateTime activityDate,
            java.time.LocalDateTime createdDate
    ) {}

    public record MeResponse(User me, List<TalkDto> talks, Set<String> cities) {}

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Not authenticated");
            pd.setType(URI.create("about:blank"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, pd.getDetail());
        }

        var username = authentication.getName();
        User me = userService.findByUsername(username)
                .orElseThrow(() -> {
                    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "User not found");
                    pd.setType(URI.create("about:blank"));
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, pd.getDetail());
                });

        me.setPassword(null);

        List<TalksRecord> talks = talkRepository.findBySpeakerId(me.getId());
        List<TalkDto> talkDtos = talks.stream()
                .map(r -> new TalkDto(
                        r.getId() == null ? null : r.getId().toString(),
                        r.getTopic(),
                        r.getTalkDate(),
                        r.getConferenceType(),
                        r.getFormat(),
                        r.getActivityName(),
                        r.getActivityDate(),
                        r.getCreatedDate()
                ))
                .toList();

        Set<String> cities = resolveCities(me);
        return new MeResponse(me, talkDtos, cities);
    }

    @PatchMapping("/me")
    public MeResponse patchMe(@RequestBody ProfilePatchRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Not authenticated");
            pd.setType(URI.create("about:blank"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, pd.getDetail());
        }

        userService.patchCurrentUser(request);
        return me(authentication);
    }

    private Set<String> resolveCities(User me) {
        String city = me.getRegion();
        if (city != null) {
            city = city.trim();
        }
        if (city == null || city.isEmpty()) {
            city = DEFAULT_CITY;
        }
        return Set.of(city);
    }
}

