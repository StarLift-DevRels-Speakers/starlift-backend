package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Системный промпт лежит в resources/prompts/{promptVersion}.txt — версия пишется в каждый сохранённый план.
 */
@Component
public class ProducerPrompt {

    private final String version;
    private final String systemText;

    public ProducerPrompt(ProducerProperties properties) {
        this.version = properties.promptVersion();
        this.systemText = load("prompts/" + version + ".txt");
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

    private static String load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Producer prompt not found: " + path, e);
        }
    }
}
