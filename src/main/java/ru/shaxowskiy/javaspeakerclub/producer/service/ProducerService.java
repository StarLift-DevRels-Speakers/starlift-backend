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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProducerService {

    public static final String STATUS_READY = "ready";
    public static final String STATUS_INSUFFICIENT_DATA = "insufficient_data";
    public static final String STATUS_FAILED = "failed";

    private static final String INSUFFICIENT_MESSAGE =
            "Пока мало данных для плана: нужны отзывы слушателей или подходящие CFP.";
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

    public List<ProducerPlanResponse> regenerateAll() {
        return userRoleRepository.findUserIdsByRole(AppRole.SPEAKER).stream()
                .limit(properties.regenerateAllLimit())
                .map(this::regenerate)
                .toList();
    }

    private boolean hasTooLittleData(SpeakerSnapshot snapshot) {
        return snapshot.feedback().size() < properties.minFeedbackForPlan() && snapshot.cfps().isEmpty();
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
        return new ProducerPlanResponse(
                record.getId().toString(),
                record.getSpeakerId(),
                record.getStatus(),
                message,
                plan,
                validation,
                record.getModel(),
                record.getPromptVersion(),
                record.getCreatedDate()
        );
    }
}
