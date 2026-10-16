package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsingJsonUtil;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;

@Component
public class SpringAiProducerLlmClient implements ProducerLlmClient {

    private final ChatClient chatClient;
    private final ProducerJson json;
    private final ProducerProperties properties;
    private final String model;

    public SpringAiProducerLlmClient(
            ChatClient.Builder chatClientBuilder,
            ProducerJson json,
            ProducerProperties properties,
            @Value("${spring.ai.openai.chat.options.model:unknown}") String model
    ) {
        this.chatClient = chatClientBuilder.build();
        this.json = json;
        this.properties = properties;
        this.model = model;
    }

    @Override
    public ProducerPlanDraft generatePlan(String systemPrompt, String userMessage) {
        // Сообщения передаём объектами: .system(String) прогоняет текст через шаблонизатор,
        // а в промпте есть фигурные скобки JSON-схемы.
        String content = chatClient.prompt()
                .messages(new SystemMessage(systemPrompt), new UserMessage(userMessage))
                .options(ChatOptions.builder().temperature(properties.temperature()).build())
                .call()
                .content();
        return json.read(ParsingJsonUtil.extractJsonObject(content), ProducerPlanDraft.class);
    }

    @Override
    public String modelName() {
        return model;
    }
}
