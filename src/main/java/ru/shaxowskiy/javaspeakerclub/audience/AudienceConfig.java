package ru.shaxowskiy.javaspeakerclub.audience;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(AudienceProperties.class)
public class AudienceConfig {

    /**
     * Профили аудитории и проверки идут в фоне: вызов LLM и разбор презентации занимают десятки секунд.
     */
    @Bean(name = "audienceTaskExecutor")
    public Executor audienceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("audience-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }
}
