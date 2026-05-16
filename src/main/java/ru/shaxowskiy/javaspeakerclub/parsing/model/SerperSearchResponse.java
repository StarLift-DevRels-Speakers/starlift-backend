package ru.shaxowskiy.javaspeakerclub.parsing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SerperSearchResponse(List<OrganicResult> organic) {

    public List<String> links() {
        if (organic == null) {
            return List.of();
        }
        return organic.stream()
                .map(OrganicResult::link)
                .filter(link -> link != null && !link.isBlank())
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrganicResult(String link) {
    }
}
