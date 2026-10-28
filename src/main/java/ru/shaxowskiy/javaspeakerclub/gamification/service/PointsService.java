package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.PointTransactionResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.PointReason;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.PointTransactionRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationPointTransactionsRecord;

import java.util.Optional;

/**
 * Журнал баллов. Неделя, в которую попадают баллы, определяется временем начисления.
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    private final PointTransactionRepository transactionRepository;
    private final SeasonService seasonService;

    /**
     * Начисляет баллы. Повторное начисление с тем же sourceId и причиной игнорируется — возвращается empty.
     */
    public Optional<GamificationPointTransactionsRecord> award(
            Long speakerId,
            PointReason reason,
            int points,
            String sourceId,
            String comment,
            Long awardedBy
    ) {
        if (points == 0) {
            return Optional.empty();
        }
        return transactionRepository.insertIfAbsent(new PointTransactionRepository.NewTransaction(
                speakerId, points, reason, sourceId, comment, awardedBy, seasonService.now()
        ));
    }

    public static PointTransactionResponse toResponse(GamificationPointTransactionsRecord record) {
        return new PointTransactionResponse(
                record.getId(),
                record.getSpeakerId(),
                record.getPoints(),
                PointReason.valueOf(record.getReason()),
                record.getSourceId(),
                record.getComment(),
                record.getAwardedBy(),
                record.getCreatedDate()
        );
    }
}
