package ru.shaxowskiy.javaspeakerclub.config;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class LiquibaseConfig {

    private final DataSource dataSource;

    /**
     * Чейнджсеты с context: demo (демо-спикеры продюсера) применяются только при APP_LIQUIBASE_CONTEXTS=demo.
     * Чейнджсеты без контекста выполняются всегда.
     */
    @Value("${app.liquibase.contexts:}")
    private String contexts;

    @Bean
    public SpringLiquibase liquibase() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setContexts(contexts == null || contexts.isBlank() ? "default" : contexts);
        liquibase.setShouldRun(true);
        return liquibase;
    }
}
