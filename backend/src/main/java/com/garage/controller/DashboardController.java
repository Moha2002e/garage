package com.garage.controller;

import com.garage.dto.DashboardResponse;
import com.garage.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Dashboard", description = "Home page of the staff: today's appointments and key figures")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    @GetMapping
    @Operation(summary = "Today's PLANNED and IN_PROGRESS appointments and key figures")
    public DashboardResponse get() {
        return dashboardService.get();
    }
}
