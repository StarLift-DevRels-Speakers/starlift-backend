package ru.shaxowskiy.javaspeakerclub.producer.llm;

import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;

public interface ProducerLlmClient {

    ProducerPlanDraft generatePlan(String systemPrompt, String userMessage);

    String modelName();
}
