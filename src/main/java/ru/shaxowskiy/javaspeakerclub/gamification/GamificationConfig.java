package ru.shaxowskiy.javaspeakerclub.gamification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(GamificationProperties.class)
public class GamificationConfig {

    /**
     * Часы с зоной сезонов: неделя начинается в понедельник 00:00 по app.gamification.zone.
     */
    @Bean
    public Clock gamificationClock(GamificationProperties properties) {
        return Clock.system(ZoneId.of(properties.zone()));
    }
}
