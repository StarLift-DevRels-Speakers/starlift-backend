package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.util.List;
import java.util.Map;

/**
 * Действия в истории взаимодействий (п. 23–24 ТЗ). Приглашение — не отдельная сущность, а цепочка фактов
 * по паре «мероприятие + эксперт»: его статус — последнее действие в цепочке.
 */
public final class Interactions {

    public static final String KIND_FACT = "fact";
    public static final String KIND_AI = "ai";

    public static final String SCENARIO_EVENT_ANALYSIS = "event_analysis";
    public static final String SCENARIO_SPEAKER_MATCH = "speaker_match";
    public static final String SCENARIO_EXPERT_EVENTS = "expert_events";

    public static final String RECOMMENDED = "recommended";

    public static final String SHORTLISTED = "shortlisted";
    public static final String INVITED = "invited";
    public static final String MARKED_IRRELEVANT = "marked_irrelevant";
    public static final String TALK_GIVEN = "talk_given";
    public static final String REPEAT_INVITATION = "repeat_invitation";

    public static final String VIEWED_EVENT = "viewed_event";
    public static final String OPENED_CFP = "opened_cfp";
    public static final String APPLIED = "applied";

    public static final String INVITATION_ACCEPTED = "invitation_accepted";
    public static final String INVITATION_DECLINED = "invitation_declined";
    public static final String NEEDS_CLARIFICATION = "needs_clarification";

    public static final List<String> ORGANIZER_ACTIONS = List.of(SHORTLISTED, INVITED, MARKED_IRRELEVANT, TALK_GIVEN);
    public static final List<String> EXPERT_EVENT_ACTIONS = List.of(VIEWED_EVENT, OPENED_CFP, APPLIED, MARKED_IRRELEVANT);
    public static final List<String> INVITATION_ACTIONS = List.of(INVITED, INVITATION_ACCEPTED, INVITATION_DECLINED, NEEDS_CLARIFICATION);
    public static final Map<String, String> INVITATION_RESPONSES = Map.of(
            "accepted", INVITATION_ACCEPTED,
            "declined", INVITATION_DECLINED,
            "needs_clarification", NEEDS_CLARIFICATION);

    private Interactions() {
    }

    public static String label(String action) {
        return switch (action) {
            case RECOMMENDED -> "ИИ рекомендовал";
            case SHORTLISTED -> "Добавлен в shortlist";
            case INVITED -> "Приглашение отправлено";
            case MARKED_IRRELEVANT -> "Отмечен как неподходящий";
            case TALK_GIVEN -> "Выступление состоялось";
            case REPEAT_INVITATION -> "Повторное приглашение";
            case VIEWED_EVENT -> "Посмотрел мероприятие";
            case OPENED_CFP -> "Открыл CFP";
            case APPLIED -> "Подал заявку";
            case INVITATION_ACCEPTED -> "Приглашение принято";
            case INVITATION_DECLINED -> "Приглашение отклонено";
            case NEEDS_CLARIFICATION -> "Требуется уточнение";
            default -> action;
        };
    }

    /** Статус приглашения по последнему действию: sent, accepted, declined или needs_clarification. */
    public static String invitationStatus(String lastAction) {
        return switch (lastAction) {
            case INVITATION_ACCEPTED -> "accepted";
            case INVITATION_DECLINED -> "declined";
            case NEEDS_CLARIFICATION -> "needs_clarification";
            default -> "sent";
        };
    }

    public static String invitationStatusLabel(String status) {
        return switch (status) {
            case "accepted" -> "Принято";
            case "declined" -> "Отклонено";
            case "needs_clarification" -> "Требуется уточнение";
            default -> "Отправлено";
        };
    }
}
