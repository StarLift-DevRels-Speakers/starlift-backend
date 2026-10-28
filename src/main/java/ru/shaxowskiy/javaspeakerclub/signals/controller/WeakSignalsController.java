package ru.shaxowskiy.javaspeakerclub.signals.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.signals.client.WeakSignalsClient;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentScoreResponse;
import ru.shaxowskiy.javaspeakerclub.signals.service.TalentRequestBuilder;

@RestController
@RequestMapping("/api/signals/speakers/{speakerId}")
@RequiredArgsConstructor
public class WeakSignalsController {

    private final TalentRequestBuilder requestBuilder;
    private final WeakSignalsClient client;

    @PostMapping("/talent-score")
    public TalentScoreResponse score(@PathVariable Long speakerId) {
        return client.score(requestBuilder.build(speakerId));
    }

    /**
     * Что уйдёт в weak-signals для спикера — без вызова LLM.
     */
    @GetMapping("/talent-request")
    public TalentRequest preview(@PathVariable Long speakerId) {
        return requestBuilder.build(speakerId);
    }
}
