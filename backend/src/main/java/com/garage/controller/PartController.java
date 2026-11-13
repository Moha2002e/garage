package com.garage.controller;

import com.garage.dto.PageResponse;
import com.garage.dto.PartDtos.PartRequest;
import com.garage.dto.PartDtos.PartResponse;
import com.garage.dto.PartDtos.StockAdjustRequest;
import com.garage.dto.PartDtos.StockMovementResponse;
import com.garage.service.PartService;
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
@RequestMapping("/api/parts")
@Tag(name = "Parts", description = "Parts catalogue and stock")
public class PartController {

    private final PartService partService;

    public PartController(PartService partService) {
        this.partService = partService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Search parts (page size 10 or 25); active and low stock filters optional")
    public PageResponse<PartResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(defaultValue = "false") boolean lowStock,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return partService.search(q, active, lowStock, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get a part")
    public PartResponse get(@PathVariable UUID id) {
        return partService.get(id);
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Stock journal of a part, newest first (page size 1 to 50, default 10)")
    public PageResponse<StockMovementResponse> movements(@PathVariable UUID id,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return partService.movements(id, page, size);
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a part (the stock starts at zero)")
    public PartResponse create(@Valid @RequestBody PartRequest request) {
        return partService.create(request);
    }

    @PostMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Add, remove or correct stock; writes a journal line and returns the updated part")
    public PartResponse adjustStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustRequest request) {
        return partService.adjustStock(id, request);
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Update a part (never changes the stock)")
    public PartResponse update(@PathVariable UUID id, @Valid @RequestBody PartRequest request) {
        return partService.update(id, request);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Deactivate a part (no physical deletion)")
    public PartResponse deactivate(@PathVariable UUID id) {
        return partService.setActive(id, false);
    }

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Reactivate a part")
    public PartResponse activate(@PathVariable UUID id) {
        return partService.setActive(id, true);
    }
}
