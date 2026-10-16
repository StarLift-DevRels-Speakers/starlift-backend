package ru.shaxowskiy.javaspeakerclub.parsing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingRunAsyncExecutor {

    private final ConferenceParsingService conferenceParsingService;

    @Async("parsingTaskExecutor")
    public void execute(ParsingRunRequest request) {
        try {
            conferenceParsingService.run(request);
        } catch (Exception e) {
            log.error("Background parsing run failed for date={}", request.date(), e);
        }
    }
}
