package ru.shaxowskiy.javaspeakerclub.audience.profile;

import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.producer.llm.PromptFiles;

/**
 * Системный промпт профиля аудитории: resources/prompts/{profilePromptVersion}.txt.
 */
@Component
public class EventAudiencePrompt {

    private final String version;
    private final String systemText;

    public EventAudiencePrompt(AudienceProperties properties) {
        this.version = properties.profilePromptVersion();
        this.systemText = PromptFiles.load(version);
    }

    public String version() {
        return version;
    }

    public String system() {
        return systemText;
    }

    public String user(String inputsJson) {
        return "Данные мероприятия:\n" + inputsJson;
    }
}
