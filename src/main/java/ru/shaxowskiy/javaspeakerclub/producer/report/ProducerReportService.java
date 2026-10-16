package ru.shaxowskiy.javaspeakerclub.producer.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerPlansRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.model.PlanValidation;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerPlanRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProducerReportService {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final ProducerPlanRepository planRepository;
    private final DemoGolden demoGolden;
    private final ProducerJson json;
    private final ProducerReportRenderer renderer;

    @Transactional(readOnly = true)
    public String renderLatestPlans() {
        List<Long> speakerIds = userRoleRepository.findUserIdsByRole(AppRole.SPEAKER);
        Map<Long, ProducerPlansRecord> plans = planRepository.findLatestBySpeakerIds(speakerIds);

        List<ProducerReportRenderer.Row> rows = userRepository.findByIds(speakerIds).stream()
                .map(user -> toRow(user, plans.get(user.getId())))
                // Сначала демо-спикеры из эталона, затем остальные по имени.
                .sorted((left, right) -> {
                    boolean leftDemo = demoGolden.covers(left.username());
                    boolean rightDemo = demoGolden.covers(right.username());
                    if (leftDemo != rightDemo) {
                        return leftDemo ? -1 : 1;
                    }
                    return left.name().compareToIgnoreCase(right.name());
                })
                .toList();
        return renderer.render(rows, LocalDateTime.now());
    }

    private ProducerReportRenderer.Row toRow(UsersRecord user, ProducerPlansRecord record) {
        ProducerPlan plan = record == null || record.getPlanJson() == null
                ? null
                : json.read(record.getPlanJson(), ProducerPlan.class);
        return new ProducerReportRenderer.Row(
                user.getId(),
                user.getUsername(),
                displayName(user),
                record == null ? null : record.getStatus(),
                record == null ? null : record.getCreatedDate(),
                record == null ? null : record.getModel(),
                record == null ? null : record.getPromptVersion(),
                record == null ? null : record.getError(),
                record == null || record.getSnapshotJson() == null
                        ? null
                        : json.read(record.getSnapshotJson(), SpeakerSnapshot.class),
                plan,
                record == null || record.getValidationJson() == null
                        ? null
                        : json.read(record.getValidationJson(), PlanValidation.class),
                demoGolden.covers(user.getUsername()) ? demoGolden.check(user.getUsername(), plan) : null
        );
    }

    private static String displayName(UsersRecord user) {
        String full = Stream.of(user.getFirstName(), user.getLastName(), user.getSurName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return full.isBlank() ? user.getUsername() : full;
    }
}
