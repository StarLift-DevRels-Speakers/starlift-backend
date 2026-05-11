package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.DevRelDashboardReport;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.DevRelDashboardReportRequest;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.ReportFiltersResponse;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.SpeakerDashboardReport;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.SpeakerDashboardReportRequest;
import ru.shaxowskiy.javaspeakerclub.service.ReportsService;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportsController {

    private final ReportsService reportsService;

    @GetMapping("/filters")
    public ReportFiltersResponse getFilters() {
        log.info("Fetching report filter options");
        return reportsService.getFilters();
    }

    @PostMapping("/speaker-dashboard/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public SpeakerDashboardReport generateSpeakerDashboard(@RequestBody(required = false) SpeakerDashboardReportRequest request) {
        log.info("Generating speaker dashboard report, request={}", request);
        return reportsService.generateSpeakerReport(request);
    }

    @PostMapping("/devrel-dashboard/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public DevRelDashboardReport generateDevRelDashboard(@RequestBody(required = false) DevRelDashboardReportRequest request) {
        log.info("Generating DevRel dashboard report, request={}", request);
        return reportsService.generateDevRelReport(request);
    }
}
