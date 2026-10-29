package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ProducerRunsRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.matching.analysis.EventCardAssembler;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.MetricsView;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.MetricsView.Share;
import ru.shaxowskiy.javaspeakerclub.producer.matching.api.MetricsView.Step;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Interactions;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerInteractionRepository.MetricRow;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerRunRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Метрики качества (п. 28 ТЗ) без отдельного хранилища: воронки считаются по истории взаимодействий,
 * доли — по журналу запусков. Целевые значения ТЗ не задаёт, поэтому здесь только факт.
 */
@Service
@RequiredArgsConstructor
public class ProducerMetricsService {

    private final ProducerInteractionRepository interactionRepository;
    private final ProducerRunRepository runRepository;
    private final ProducerJson json;

    @Transactional(readOnly = true)
    public MetricsView metrics() {
        List<MetricRow> rows = interactionRepository.findForMetrics();
        Predicate<MetricRow> organizerScope = row -> row.eventRequestId() != null && row.cfpId() == null;
        Predicate<MetricRow> expertScope = row -> row.cfpId() != null;

        List<Step> organizer = funnel(rows, organizerScope, List.of(
                Interactions.RECOMMENDED, Interactions.SHORTLISTED, Interactions.INVITED,
                Interactions.INVITATION_ACCEPTED, Interactions.TALK_GIVEN));
        List<Step> expert = funnel(rows, expertScope, List.of(
                Interactions.RECOMMENDED, Interactions.VIEWED_EVENT, Interactions.OPENED_CFP, Interactions.APPLIED));

        List<Share> quality = new ArrayList<>();
        List<String> confidences = rows.stream()
                .filter(row -> Interactions.KIND_AI.equals(row.kind()) && Interactions.RECOMMENDED.equals(row.action()))
                .map(MetricRow::confidence)
                .toList();
        quality.add(share("Рекомендации с низкой уверенностью",
                (int) confidences.stream().filter(Recommendation.CONFIDENCE_LOW::equals).count(), confidences.size()));

        List<ProducerRunsRecord> analyses = runRepository.findByScenario(Interactions.SCENARIO_EVENT_ANALYSIS);
        int withQuestions = (int) analyses.stream().filter(this::hasQuestions).count();
        quality.add(share("Мероприятия, где понадобились уточняющие вопросы", withQuestions, analyses.size()));

        Map<String, Integer> matchesPerEvent = new HashMap<>();
        runRepository.findByScenario(Interactions.SCENARIO_SPEAKER_MATCH)
                .forEach(run -> matchesPerEvent.merge(run.getObjectId(), 1, Integer::sum));
        int rematched = (int) matchesPerEvent.values().stream().filter(count -> count > 1).count();
        quality.add(share("Мероприятия, где организатор менял требования и подбирал заново", rematched, matchesPerEvent.size()));

        int declined = countPairs(rows, organizerScope, Interactions.INVITATION_DECLINED);
        int invited = countPairs(rows, organizerScope, Interactions.INVITED);
        quality.add(share("Отказы спикеров от приглашений (не считаются негативом в подборе)", declined, invited));
        return new MetricsView(organizer, expert, quality);
    }

    private boolean hasQuestions(ProducerRunsRecord run) {
        if (run.getResultJson() == null) {
            return false;
        }
        EventCard card = json.read(run.getResultJson(), EventCard.class);
        return card.questions() != null && card.questions().stream()
                .anyMatch(question -> !EventCardAssembler.QUESTION_ANSWERED.equals(question.status()));
    }

    private static List<Step> funnel(List<MetricRow> rows, Predicate<MetricRow> scope, List<String> actions) {
        List<Step> steps = new ArrayList<>();
        Integer previous = null;
        for (String action : actions) {
            int count = countPairs(rows, scope, action);
            Integer conversion = previous == null || previous == 0 ? null : Math.round(count * 100f / previous);
            steps.add(new Step(Interactions.label(action), count, conversion));
            previous = count;
        }
        return steps;
    }

    /** Разные пары «эксперт + объект»: повторный подбор того же кандидата не раздувает воронку. */
    private static int countPairs(List<MetricRow> rows, Predicate<MetricRow> scope, String action) {
        Set<String> pairs = new HashSet<>();
        rows.stream()
                .filter(scope)
                .filter(row -> action.equals(row.action()))
                .forEach(row -> pairs.add(row.expertId() + "|" + row.eventRequestId() + "|" + row.cfpId()));
        return pairs.size();
    }

    private static Share share(String metric, int part, int total) {
        return new Share(metric, part, total, total == 0 ? null : Math.round(part * 100f / total));
    }
}
