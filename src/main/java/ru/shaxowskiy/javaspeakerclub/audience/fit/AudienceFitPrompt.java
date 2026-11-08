package ru.shaxowskiy.javaspeakerclub.audience.fit;

import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.producer.llm.PromptFiles;

/**
 * Системный промпт проверки: resources/prompts/{fitPromptVersion}.txt.
 */
@Component
public class AudienceFitPrompt {

    private final String version;
    private final String systemText;

    public AudienceFitPrompt(AudienceProperties properties) {
        this.version = properties.fitPromptVersion();
        this.systemText = PromptFiles.load(version);
    }

    public String version() {
        return version;
    }

    public String system() {
        return systemText;
    }

    public String user(String eventName, String profileJson, String materialJson) {
        return "Мероприятие: «" + eventName + "».\nПрофиль ожидаемой аудитории:\n" + profileJson
                + "\n\nМатериал спикера (фрагменты с id):\n" + materialJson;
    }
}
