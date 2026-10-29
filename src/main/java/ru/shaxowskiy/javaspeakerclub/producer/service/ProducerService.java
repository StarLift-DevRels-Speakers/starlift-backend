package ru.shaxowskiy.javaspeakerclub.producer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerPlansRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerPrompt;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;
import ru.shaxowskiy.javaspeakerclub.producer.model.PlanValidation;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanResponse;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot;
import ru.shaxowskiy.javaspeakerclub.producer.validation.ProducerPlanValidator;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerPlanRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerPlanRepository.NewPlan;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProducerService {

    public static final String STATUS_READY = "ready";
    public static final String STATUS_INSUFFICIENT_DATA = "insufficient_data";
    public static final String STATUS_FAILED = "failed";

    private static final String INSUFFICIENT_MESSAGE =
            "Пока мало данных для плана: нужно хотя бы три отзыва слушателей или подходящий CFP. Что можно дополнить — в gaps.";
    private static final String FAILED_MESSAGE =
            "Не удалось сформировать план. Попробуйте ещё раз позже.";

    private final SpeakerSnapshotService snapshotService;
    private final ProducerLlmClient llmClient;
    private final ProducerPrompt prompt;
    private final ProducerPlanValidator validator;
    private final ProducerPlanRepository planRepository;
    private final UserRoleRepository userRoleRepository;
    private final ProducerJson json;
    private final ProducerProperties properties;

    /**
     * Без общей транзакции: снимок читается в своей read-only транзакции,
     * вызов LLM идёт вне транзакции, результат сохраняется отдельной вставкой.
     */
    public ProducerPlanResponse regenerate(Long speakerId) {
        SpeakerSnapshot snapshot = snapshotService.build(speakerId);
        String snapshotJson = json.write(snapshot);
        LocalDate today = snapshot.generatedOn();

        if (hasTooLittleData(snapshot)) {
            return toResponse(planRepository.insert(new NewPlan(
                    speakerId, STATUS_INSUFFICIENT_DATA, null, snapshotJson, null, null, prompt.version(), null
            )));
        }

        ProducerPlanDraft draft;
        try {
            draft = llmClient.generatePlan(prompt.system(), prompt.user(snapshotJson, today));
        } catch (RuntimeException e) {
            log.warn("Producer plan generation failed for speakerId={}: {}", speakerId, e.getMessage());
            return toResponse(planRepository.insert(new NewPlan(
                    speakerId, STATUS_FAILED, null, snapshotJson, null,
                    llmClient.modelName(), prompt.version(), e.getClass().getSimpleName() + ": " + e.getMessage()
            )));
        }

        ProducerPlanValidator.Result result = validator.validate(draft, snapshot, today);
        boolean empty = result.plan().strengths().isEmpty()
                && result.plan().growthAreas().isEmpty()
                && result.plan().steps().isEmpty();
        return toResponse(planRepository.insert(new NewPlan(
                speakerId,
                empty ? STATUS_INSUFFICIENT_DATA : STATUS_READY,
                json.write(result.plan()),
                snapshotJson,
                json.write(result.validation()),
                llmClient.modelName(),
                prompt.version(),
                null
        )));
    }

    @Transactional(readOnly = true)
    public ProducerPlanResponse getLatest(Long speakerId) {
        return planRepository.findLatestBySpeakerId(speakerId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "План ещё не сформирован. Запустите POST, чтобы собрать его."));
    }

    /**
     * История планов спикера, старые первыми.
     */
    public List<ProducerPlanResponse> getHistory(Long speakerId) {
        return planRepository.findAllBySpeakerId(speakerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProducerPlanResponse> regenerateAll() {
        return userRoleRepository.findUserIdsByRole(AppRole.SPEAKER).stream()
                .limit(properties.regenerateAllLimit())
                .map(this::regenerate)
                .toList();
    }

    private boolean hasTooLittleData(SpeakerSnapshot snapshot) {
        return snapshot.feedback().size() < properties.minFeedbackForPlan() && snapshot.cfps().isEmpty();
    }

    /**
     * Уверенность плана считает код, а не модель (п. 19 ТЗ): чем больше свежих отзывов и выступлений, тем выше.
     */
    static Recommendation.Confidence confidence(SpeakerSnapshot snapshot) {
        int feedback = snapshot.feedback() == null ? 0 : snapshot.feedback().size();
        Integer days = snapshot.activity() == null ? null : snapshot.activity().daysSinceLastTalk();
        List<String> reasons = new ArrayList<>();
        reasons.add("Отзывов за год: " + feedback + ".");
        reasons.add(days == null ? "Выступлений на платформе нет." : "Последнее выступление — " + days + " дн. назад.");
        String level;
        if (feedback >= 10 && days != null && days <= 120) {
            level = Recommendation.CONFIDENCE_HIGH;
        } else if (feedback >= 3 && days != null && days <= 365) {
            level = Recommendation.CONFIDENCE_MEDIUM;
        } else {
            level = Recommendation.CONFIDENCE_LOW;
        }
        return new Recommendation.Confidence(level, reasons);
    }

    private static List<String> gaps(SpeakerSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        List<String> gaps = new ArrayList<>();
        int feedback = snapshot.feedback() == null ? 0 : snapshot.feedback().size();
        if (feedback < 3) {
            gaps.add("Отзывов за год: " + feedback + ". Чтобы увидеть повторяющиеся темы, нужно хотя бы три.");
        }
        if (snapshot.cfps() == null || snapshot.cfps().isEmpty()) {
            gaps.add("Нет открытых CFP, подходящих к вашей экспертизе: дополните теги докладов и интересы.");
        }
        if (snapshot.profileGaps() != null) {
            gaps.addAll(snapshot.profileGaps());
        }
        return gaps;
    }

    ProducerPlanResponse toResponse(ProducerPlansRecord record) {
        ProducerPlan plan = record.getPlanJson() == null ? null : json.read(record.getPlanJson(), ProducerPlan.class);
        PlanValidation validation = record.getValidationJson() == null
                ? null
                : json.read(record.getValidationJson(), PlanValidation.class);
        String message = switch (record.getStatus()) {
            case STATUS_INSUFFICIENT_DATA -> INSUFFICIENT_MESSAGE;
            case STATUS_FAILED -> FAILED_MESSAGE;
            default -> null;
        };
        SpeakerSnapshot snapshot = record.getSnapshotJson() == null
                ? null
                : json.read(record.getSnapshotJson(), SpeakerSnapshot.class);
        return new ProducerPlanResponse(
                record.getId().toString(),
                record.getSpeakerId(),
                record.getStatus(),
                message,
                plan,
                validation,
                snapshot == null ? null : confidence(snapshot),
                gaps(snapshot),
                record.getModel(),
                record.getPromptVersion(),
                record.getCreatedDate()
        );
    }
}
