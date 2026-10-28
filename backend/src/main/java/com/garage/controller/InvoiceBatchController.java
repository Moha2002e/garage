package com.garage.controller;

import com.garage.dto.InvoiceDtos.BatchErrorResponse;
import com.garage.dto.InvoiceDtos.BatchRunResponse;
import com.garage.dto.PageResponse;
import com.garage.service.InvoiceBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices/batch")
// Applies to every method of this controller: a CLIENT gets 403 on all of them.
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Invoice batch", description = "Spring Batch job that invoices completed appointments (ADMIN and EMPLOYEE only)")
public class InvoiceBatchController {

    private final InvoiceBatchService batchService;

    public InvoiceBatchController(InvoiceBatchService batchService) {
        this.batchService = batchService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Runs of the invoice job, most recent first (page size 10 or 25)")
    public PageResponse<BatchRunResponse> runs(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return batchService.runs(page, size);
    }

    @GetMapping("/errors")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Appointments ignored by the job, newest first (page size 10 or 25); runId is optional")
    public PageResponse<BatchErrorResponse> errors(@RequestParam(required = false) Long runId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return batchService.errors(runId, page, size);
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Run the invoice job now and wait for its end (409 when a run is already in progress)")
    public BatchRunResponse run() {
        return batchService.run();
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    // Pas de PUT : l'historique est en lecture seule.

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : rien n'est supprimé.
}
