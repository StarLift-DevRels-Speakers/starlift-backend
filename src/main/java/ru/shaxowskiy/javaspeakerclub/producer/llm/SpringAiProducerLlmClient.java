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
            @Value("${spring.ai.gigachat.chat.model:unknown}") String model
    ) {
        this.chatClient = chatClientBuilder.build();
        this.json = json;
        this.properties = properties;
        this.model = model;
    }

    @Override
    public ProducerPlanDraft generatePlan(String systemPrompt, String userMessage) {
        return generate(systemPrompt, userMessage, ProducerPlanDraft.class, null);
    }

    @Override
    public <T> T generate(String systemPrompt, String userMessage, Class<T> type, Integer maxTokens) {
        // Сообщения передаём объектами: .system(String) прогоняет текст через шаблонизатор,
        // а в промпте есть фигурные скобки JSON-схемы.
        String content = chatClient.prompt()
                .messages(new SystemMessage(systemPrompt), new UserMessage(userMessage))
                .options(ChatOptions.builder().temperature(properties.temperature()).maxTokens(maxTokens))
                .call()
                .content();
        return parse(content, type);
    }

    /**
     * Ответ как есть, затем с дописанными скобками, затем с экранированными кавычками внутри строк.
     * Если не помогло ничего, бросается исходная ошибка разбора.
     */
    <T> T parse(String content, Class<T> type) {
        try {
            return json.read(ParsingJsonUtil.extractJsonObject(content), type);
        } catch (RuntimeException e) {
            String escaped = JsonRepair.escapeInnerQuotes(content);
            for (String candidate : new String[]{
                    JsonRepair.closeUnbalanced(content),
                    escaped,
                    JsonRepair.closeUnbalanced(escaped)
            }) {
                if (candidate == null) {
                    continue;
                }
                try {
                    return json.read(ParsingJsonUtil.extractJsonObject(candidate), type);
                } catch (RuntimeException ignored) {
                    // следующий вариант починки
                }
            }
            throw e;
        }
    }

    @Override
    public String modelName() {
        return model;
    }
}
