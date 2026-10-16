package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Промпты лежат в resources/prompts/{version}.txt. Версия пишется в журнал каждого запуска.
 */
public final class PromptFiles {

    private PromptFiles() {
    }

    public static String load(String version) {
        String path = "prompts/" + version + ".txt";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Producer prompt not found: " + path, e);
        }
    }
}
