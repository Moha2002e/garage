package com.garage.controller;

import com.garage.dto.AppointmentDtos.BrandCount;
import com.garage.dto.AppointmentDtos.MonthRevenue;
import com.garage.dto.AppointmentDtos.StatisticsResponse;
import com.garage.dto.AppointmentDtos.StatusCount;
import com.garage.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
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
    @Operation(summary = "Planned, in progress, completed and cancelled appointments for each month of a year")
    public StatisticsResponse appointments(@RequestParam(required = false) Integer year) {
        return statisticsService.monthly(year == null ? LocalDate.now().getYear() : year);
    }

    @GetMapping("/status-distribution")
    @Operation(summary = "Number of appointments per status for a year (default: current year)")
    public List<StatusCount> statusDistribution(@RequestParam(required = false) Integer year) {
        return statisticsService.statusDistribution(year == null ? LocalDate.now().getYear() : year);
    }

    @GetMapping("/revenue-by-month")
    @Operation(summary = "Revenue per month (1 to 12) of a year: ACCEPTED quotes of COMPLETED appointments")
    public List<MonthRevenue> revenueByMonth(@RequestParam(required = false) Integer year) {
        return statisticsService.revenueByMonth(year == null ? LocalDate.now().getYear() : year);
    }

    @GetMapping("/top-brands")
    @Operation(summary = "Most frequent brands among active vehicles (limit 1 to 20, default 5)")
    public List<BrandCount> topBrands(@RequestParam(defaultValue = "5") int limit) {
        return statisticsService.topBrands(limit);
    }
}
