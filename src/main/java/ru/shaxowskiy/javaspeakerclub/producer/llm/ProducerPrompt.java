package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;

import java.time.LocalDate;

/**
 * Системный промпт плана — resources/prompts/{promptVersion}.txt, версия пишется в каждый сохранённый план.
 */
@Component
public class ProducerPrompt {

    private final String version;
    private final String systemText;

    public ProducerPrompt(ProducerProperties properties) {
        this.version = properties.promptVersion();
        this.systemText = PromptFiles.load(version);
    }

    public String version() {
        return version;
    }

    public String system() {
        return systemText;
    }

    public String user(String snapshotJson, LocalDate today) {
        return "Сегодня: " + today + ".\nСнимок данных спикера:\n" + snapshotJson;
    }
}
