package ru.shaxowskiy.javaspeakerclub.audience.fit;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitModels.AudienceFitCheckResponse;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitModels.AudienceFitRequest;

import java.util.List;
import java.util.UUID;

/**
 * Проверки докладов и презентаций под ожидаемую аудиторию мероприятия.
 * POST отвечает сразу со статусом pending; результат появляется в GET, когда проверка закончится.
 */
@RestController
@RequestMapping("/api/audience-fit")
@RequiredArgsConstructor
public class AudienceFitController {

    private final AudienceFitService service;

    @PostMapping("/checks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AudienceFitCheckResponse request(@RequestBody AudienceFitRequest request) {
        return service.request(request);
    }

    @GetMapping("/checks")
    public List<AudienceFitCheckResponse> find(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) UUID talkId,
            @RequestParam(required = false) UUID mediaId
    ) {
        return service.find(eventId, talkId, mediaId);
    }

    @GetMapping("/checks/{id}")
    public AudienceFitCheckResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
