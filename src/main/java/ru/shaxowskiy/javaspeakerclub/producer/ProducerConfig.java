package ru.shaxowskiy.javaspeakerclub.producer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.shaxowskiy.javaspeakerclub.producer.llm.LlmProperties;
import ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingProperties;

@Configuration
@EnableConfigurationProperties({ProducerProperties.class, MatchingProperties.class, LlmProperties.class})
public class ProducerConfig {
}
