package com.garage.controller;

import com.garage.domain.AppointmentStatus;
import com.garage.dto.AppointmentDtos.AppointmentRequest;
import com.garage.dto.AppointmentDtos.AppointmentResponse;
import com.garage.dto.AppointmentDtos.CalendarEvent;
import com.garage.dto.AppointmentDtos.RescheduleRequest;
import com.garage.dto.AppointmentDtos.StatusRequest;
import com.garage.dto.PageResponse;
import com.garage.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Garage appointments; a CLIENT only sees their own")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    @GetMapping
    @Operation(summary = "Search appointments (page size 10 or 25); dates are Europe/Paris days")
    public PageResponse<AppointmentResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return appointmentService.search(q, status, from, to, page, size);
    }

    @GetMapping("/calendar")
    @Operation(summary = "Calendar events overlapping [from, to) (ISO instants, 100 days at most, no pagination); a CLIENT only sees their own")
    public List<CalendarEvent> calendar(@RequestParam Instant from, @RequestParam Instant to) {
        return appointmentService.calendar(from, to);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an appointment")
    public AppointmentResponse get(@PathVariable UUID id) {
        return appointmentService.get(id);
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an appointment (past dates allowed; 409 on schedule conflict)")
    public AppointmentResponse create(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.create(request);
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Update a planned appointment (409 on schedule conflict)")
    public AppointmentResponse update(@PathVariable UUID id, @Valid @RequestBody AppointmentRequest request) {
        return appointmentService.update(id, request);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : un rendez-vous s'annule via le statut CANCELLED.

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Change the status: PLANNED to IN_PROGRESS, COMPLETED or CANCELLED; IN_PROGRESS to COMPLETED or CANCELLED")
    public AppointmentResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return appointmentService.changeStatus(id, request.status());
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Move a planned appointment to a new start (same duration); 400 if not PLANNED or date out of range, 409 on schedule conflict")
    public AppointmentResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        return appointmentService.reschedule(id, request.date());
    }
}
