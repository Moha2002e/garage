package com.garage.controller;

import com.garage.dto.ClientDtos.ClientRequest;
import com.garage.dto.ClientDtos.ClientResponse;
import com.garage.dto.PageResponse;
import com.garage.dto.AppointmentDtos.AppointmentResponse;
import com.garage.service.AppointmentService;
import com.garage.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Clients", description = "Garage clients")
public class ClientController {

    private final ClientService clientService;
    private final AppointmentService appointmentService;

    public ClientController(ClientService clientService, AppointmentService appointmentService) {
        this.clientService = clientService;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    @Operation(summary = "Search clients (page size 10 or 25); active filter optional")
    public PageResponse<ClientResponse> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return clientService.search(q, active, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a client")
    public ClientResponse get(@PathVariable UUID id) {
        return clientService.get(id);
    }

    @GetMapping("/{id}/appointments")
    @Operation(summary = "Appointments of a client, newest first (page size 1 to 50, default 10)")
    public PageResponse<AppointmentResponse> appointments(@PathVariable UUID id,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return appointmentService.listByClient(id, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a client")
    public ClientResponse create(@Valid @RequestBody ClientRequest request) {
        return clientService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a client")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a client (no physical deletion)")
    public ClientResponse deactivate(@PathVariable UUID id) {
        return clientService.setActive(id, false);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate a client")
    public ClientResponse activate(@PathVariable UUID id) {
        return clientService.setActive(id, true);
    }
}
