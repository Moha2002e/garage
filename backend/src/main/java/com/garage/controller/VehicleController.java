package com.garage.controller;

import com.garage.dto.PageResponse;
import com.garage.dto.VehicleDtos.VehicleRequest;
import com.garage.dto.VehicleDtos.VehicleResponse;
import com.garage.dto.AppointmentDtos.AppointmentResponse;
import com.garage.service.AppointmentService;
import com.garage.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Vehicles", description = "Client vehicles (the plate is personal data)")
public class VehicleController {

    private final VehicleService vehicleService;
    private final AppointmentService appointmentService;

    public VehicleController(VehicleService vehicleService, AppointmentService appointmentService) {
        this.vehicleService = vehicleService;
        this.appointmentService = appointmentService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    @GetMapping("/vehicles")
    @Operation(summary = "Search vehicles (page size 10 or 25); active filter optional, absent means all")
    public PageResponse<VehicleResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return vehicleService.search(q, active, page, size);
    }

    @GetMapping("/clients/{clientId}/vehicles")
    @Operation(summary = "List the vehicles of a client; active=true keeps only active ones, absent means all")
    public List<VehicleResponse> listByClient(@PathVariable UUID clientId,
                                              @RequestParam(required = false) Boolean active) {
        return vehicleService.listByClient(clientId, active);
    }

    @GetMapping("/vehicles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE','CLIENT')")
    @Operation(summary = "Get a vehicle with its client; a CLIENT only gets their own vehicles (404 otherwise)")
    public VehicleResponse get(@PathVariable UUID id) {
        return vehicleService.get(id);
    }

    @GetMapping("/vehicles/{id}/appointments")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE','CLIENT')")
    @Operation(summary = "Appointments of a vehicle, newest first (page size 1 to 50, default 10); a CLIENT only for their own vehicles")
    public PageResponse<AppointmentResponse> appointments(@PathVariable UUID id,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return appointmentService.listByVehicle(id, page, size);
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a vehicle")
    public VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        return vehicleService.create(request);
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    @PutMapping("/vehicles/{id}")
    @Operation(summary = "Update a vehicle")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    @DeleteMapping("/vehicles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a vehicle (no physical deletion); refused with planned or in progress appointments")
    public void deactivate(@PathVariable UUID id) {
        vehicleService.deactivate(id);
    }

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    @PatchMapping("/vehicles/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate a vehicle")
    public VehicleResponse reactivate(@PathVariable UUID id) {
        return vehicleService.reactivate(id);
    }
}
