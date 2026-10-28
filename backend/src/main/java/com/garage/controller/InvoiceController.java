package com.garage.controller;

import com.garage.domain.InvoiceStatus;
import com.garage.dto.InvoiceDtos.InvoiceDetailResponse;
import com.garage.dto.InvoiceDtos.InvoiceResponse;
import com.garage.dto.PageResponse;
import com.garage.service.InvoicePdfService;
import com.garage.service.InvoicePdfService.PdfDocument;
import com.garage.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
// Applies to every method of this controller: a CLIENT gets 403 on all of them.
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@Tag(name = "Invoices", description = "Invoices created by the invoice batch (ADMIN and EMPLOYEE only)")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Search invoices, newest first (page size 10 or 25, q 100 characters at most)")
    public PageResponse<InvoiceResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return invoiceService.search(q, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get an invoice with its lines")
    public InvoiceDetailResponse get(@PathVariable UUID id) {
        return invoiceService.get(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Download the invoice as a PDF (404 when unknown, file name is the invoice number)")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        PdfDocument document = invoicePdfService.generate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.filename()).build().toString())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .body(document.content());
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    // Pas de POST ici : les factures sont créées par POST /api/invoices/batch/run.

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Mark an invoice as PAID (409 when it is already paid)")
    public InvoiceDetailResponse pay(@PathVariable UUID id) {
        return invoiceService.pay(id);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : une facture n'est jamais supprimée.
}
