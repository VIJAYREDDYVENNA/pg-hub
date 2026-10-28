package com.hostelmanagement.dashboard;

import com.hostelmanagement.dashboard.DashboardService.Activity;
import com.hostelmanagement.dashboard.DashboardService.DashboardSummary;
import com.hostelmanagement.dashboard.VacancyForecastService.VacancyForecast;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_HOSTEL_VIEW')")
public class DashboardController {

    private final DashboardService service;
    private final VacancyForecastService vacancyService;

    @Operation(summary = "Summary cards (all values computed from the database)")
    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(required = false) Long hostelId) {
        return service.summary(hostelId);
    }

    @Operation(summary = "Vacancy forecast. Use days (7/15/30) or an explicit from/to range")
    @GetMapping("/vacancies")
    public VacancyForecast vacancies(@RequestParam(required = false) Long hostelId,
                                     @RequestParam(required = false) Integer days,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return vacancyService.forecast(hostelId, days, from, to);
    }

    @GetMapping("/activity")
    public Activity activity(@RequestParam(required = false) Long hostelId) {
        return service.activity(hostelId);
    }
}
