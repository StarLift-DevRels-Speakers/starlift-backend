package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CfpRecommendationResponse(
        String id,
        String title,
        String city,
        DashboardInfluenceDto influenceLevel,
        String topic,
        LocalDate deadline,
        String hostCompany,
        String url,
        Boolean active,
        List<String> tags,
        LocalDateTime createdDate
) {
}
