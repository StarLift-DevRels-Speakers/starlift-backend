package ru.shaxowskiy.javaspeakerclub.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ParsingProperties.class)
public class ParsingConfig {

    @Bean
    WebClient parsingWebClient(ParsingProperties properties) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs()
                        .maxInMemorySize(properties.maxResponseBytes()))
                .build();

        return WebClient.builder()
                .exchangeStrategies(strategies)
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
    }
}
