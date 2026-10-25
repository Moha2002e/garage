package com.garage.controller;

import com.garage.dto.VehicleDtos.VehicleRequest;
import com.garage.dto.VehicleDtos.VehicleResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Vehicles", description = "Client vehicles (the plate is personal data)")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping("/clients/{clientId}/vehicles")
    @Operation(summary = "List the vehicles of a client")
    public List<VehicleResponse> listByClient(@PathVariable UUID clientId) {
        return vehicleService.listByClient(clientId);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a vehicle")
    public VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        return vehicleService.create(request);
    }

    @PutMapping("/vehicles/{id}")
    @Operation(summary = "Update a vehicle")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @DeleteMapping("/vehicles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a vehicle without appointments")
    public void delete(@PathVariable UUID id) {
        vehicleService.delete(id);
    }
}
