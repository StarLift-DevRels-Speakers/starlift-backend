package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.gamification.GamificationProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonCloseScheduler {

    private final SeasonCloseService seasonCloseService;
    private final GamificationProperties properties;

    @Scheduled(cron = "${app.gamification.close-cron:0 5 0 * * MON}", zone = "${app.gamification.zone:Europe/Moscow}")
    public void closeExpiredSeasons() {
        if (!properties.scheduledCloseEnabled()) {
            return;
        }
        try {
            int closed = seasonCloseService.closeExpired();
            log.info("Gamification scheduler closed {} season(s)", closed);
        } catch (RuntimeException e) {
            log.error("Gamification scheduler failed to close seasons", e);
        }
    }
}
