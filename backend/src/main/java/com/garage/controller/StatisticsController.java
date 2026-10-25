package com.garage.controller;

import com.garage.dto.AppointmentDtos.StatisticsResponse;
import com.garage.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Statistics", description = "Monthly appointment statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/appointments")
    @Operation(summary = "Planned, completed and cancelled appointments for each month of a year")
    public StatisticsResponse appointments(@RequestParam(required = false) Integer year) {
        return statisticsService.monthly(year == null ? LocalDate.now().getYear() : year);
    }
}
