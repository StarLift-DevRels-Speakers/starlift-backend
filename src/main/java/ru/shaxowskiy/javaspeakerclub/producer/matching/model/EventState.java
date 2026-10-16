package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Что хранится по мероприятию: исходные данные организатора, ответ LLM и снимок требований на момент последнего подбора.
 * Карточка (EventCard) каждый раз собирается из этого состояния заново — LLM повторно вызывается только при смене описания.
 */
public record EventState(
        Map<String, List<String>> structured,
        Map<String, List<String>> answers,
        Set<String> skipped,
        List<CriterionInput> organizerCriteria,
        Set<String> rejectedCriteria,
        EventAnalysisDraft draft,
        List<String> analysisNotes,
        Map<String, List<String>> matchedParams,
        List<String> matchedCriteria
) {

    /** Критерий от организатора: обязательность задана им самим, это не интерпретация AI. */
    public record CriterionInput(String text, String kind, String check, String value) {
    }

    public EventState withDraft(EventAnalysisDraft newDraft, List<String> notes) {
        return new EventState(structured, answers, skipped, organizerCriteria, rejectedCriteria, newDraft, notes,
                matchedParams, matchedCriteria);
    }

    public EventState withMatched(Map<String, List<String>> params, List<String> criteria) {
        return new EventState(structured, answers, skipped, organizerCriteria, rejectedCriteria, draft, analysisNotes,
                params, criteria);
    }
}
