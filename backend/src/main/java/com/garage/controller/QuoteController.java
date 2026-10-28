package com.garage.controller;

import com.garage.dto.QuoteDtos.QuoteRequest;
import com.garage.dto.QuoteDtos.QuoteResponse;
import com.garage.dto.QuoteDtos.QuoteStatusRequest;
import com.garage.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments/{id}/quote")
@Tag(name = "Quotes", description = "Quote of an appointment; a CLIENT can only read the quotes of their own appointments")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    @GetMapping
    @Operation(summary = "Get the quote of an appointment, or 204 when there is none yet")
    public ResponseEntity<QuoteResponse> get(@PathVariable UUID id) {
        return quoteService.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    // Pas de POST : le PUT crée ou remplace le devis.

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Create or replace the lines of a DRAFT quote (1 to 50 lines)")
    public QuoteResponse replaceLines(@PathVariable UUID id, @Valid @RequestBody QuoteRequest request) {
        return quoteService.replaceLines(id, request);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : un devis se refuse, il n'est jamais supprimé.

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    @PatchMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Accept or refuse a DRAFT quote")
    public QuoteResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody QuoteStatusRequest request) {
        return quoteService.changeStatus(id, request.status());
    }
}
