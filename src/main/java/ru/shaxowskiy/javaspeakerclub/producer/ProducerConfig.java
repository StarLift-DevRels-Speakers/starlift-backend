package ru.shaxowskiy.javaspeakerclub.producer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProducerProperties.class)
public class ProducerConfig {
}
