package ru.shaxowskiy.javaspeakerclub.signals;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(WeakSignalsProperties.class)
public class WeakSignalsConfig {

    @Bean
    WebClient weakSignalsWebClient(WeakSignalsProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
