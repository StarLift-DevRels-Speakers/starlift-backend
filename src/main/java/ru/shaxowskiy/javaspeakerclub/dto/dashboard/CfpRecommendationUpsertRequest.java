package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

public record CfpRecommendationUpsertRequest(
        String title,
        String city,
        DashboardInfluenceDto influenceLevel,
        String topic,
        LocalDate deadline,
        String hostCompany,
        String url,
        Boolean active,
        List<String> tags
) {
}
