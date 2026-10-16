package ru.shaxowskiy.javaspeakerclub.producer.llm;

import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;

public interface ProducerLlmClient {

    ProducerPlanDraft generatePlan(String systemPrompt, String userMessage);

    /** Ответ модели — JSON-объект заданного типа (разбор описания мероприятия). */
    <T> T generate(String systemPrompt, String userMessage, Class<T> type);

    String modelName();
}
