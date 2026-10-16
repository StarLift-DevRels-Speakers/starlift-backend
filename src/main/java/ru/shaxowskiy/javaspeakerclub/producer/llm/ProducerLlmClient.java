package ru.shaxowskiy.javaspeakerclub.producer.llm;

import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;

public interface ProducerLlmClient {

    ProducerPlanDraft generatePlan(String systemPrompt, String userMessage);

    /**
     * Ответ модели — JSON-объект заданного типа. maxTokens ограничивает длину ответа: небольшая модель иногда
     * зацикливается и пишет до предела; null — без ограничения.
     */
    <T> T generate(String systemPrompt, String userMessage, Class<T> type, Integer maxTokens);

    String modelName();
}
