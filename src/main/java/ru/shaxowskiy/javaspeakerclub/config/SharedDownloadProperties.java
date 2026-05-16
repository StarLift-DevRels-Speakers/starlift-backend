package ru.shaxowskiy.javaspeakerclub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.shared-download")
public class SharedDownloadProperties {

    private String baseUrl;
}
