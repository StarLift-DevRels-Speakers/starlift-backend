package ru.shaxowskiy.javaspeakerclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ParsingConfigTest {

    @Autowired
    private ParsingProperties parsingProperties;

    @Autowired
    private WebClient parsingWebClient;

    @Test
    void parsingWebClientUsesConfiguredMaxResponseBytes() {
        assertTrue(parsingProperties.maxResponseBytes() > 262_144);
        assertTrue(parsingWebClient != null);
    }
}
