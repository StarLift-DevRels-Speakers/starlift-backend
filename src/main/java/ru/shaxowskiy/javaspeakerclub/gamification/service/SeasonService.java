package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonStatus;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.SeasonRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationSeasonsRecord;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Сезон — неделя с понедельника 00:00 до следующего понедельника. Сезон текущей недели создаётся при первом обращении.
 */
@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final Clock clock;

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    @Transactional
    public GamificationSeasonsRecord current() {
        LocalDateTime now = now();
        return seasonRepository.findCovering(now).orElseGet(() -> createCurrent(now));
    }

    @Transactional
    public SeasonResponse currentResponse() {
        return toResponse(current());
    }

    @Transactional
    public List<SeasonResponse> recent(int limit) {
        current();
        return seasonRepository.findRecent(limit).stream().map(SeasonService::toResponse).toList();
    }

    public GamificationSeasonsRecord require(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found: " + seasonId));
    }

    public static LocalDateTime weekStart(LocalDateTime moment) {
        return moment.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
    }

    public static SeasonResponse toResponse(GamificationSeasonsRecord record) {
        return new SeasonResponse(
                record.getId(),
                record.getStartsAt(),
                record.getEndsAt(),
                SeasonStatus.valueOf(record.getStatus()),
                record.getClosedAt()
        );
    }

    private GamificationSeasonsRecord createCurrent(LocalDateTime now) {
        LocalDateTime weekStart = weekStart(now);
        LocalDateTime start = seasonRepository.findLatest()
                .map(GamificationSeasonsRecord::getEndsAt)
                // Сезон, закрытый досрочно на этой неделе: следующий начинается с момента закрытия.
                .filter(end -> end.isAfter(weekStart) && !end.isAfter(now))
                .orElse(weekStart);
        seasonRepository.insertIfAbsent(start, weekStart.plusWeeks(1), now);
        return seasonRepository.findByStartsAt(start)
                .orElseThrow(() -> new IllegalStateException("Season was not created for " + start));
    }
}
