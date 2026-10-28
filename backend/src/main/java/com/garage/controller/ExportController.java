package com.garage.controller;

import com.garage.domain.AppointmentStatus;
import com.garage.domain.InvoiceStatus;
import com.garage.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Exports", description = "Excel exports (ADMIN only)")
public class ExportController {

    private static final MediaType EXCEL = MediaType.parseMediaType("application/vnd.ms-excel");

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/clients.xls")
    @Operation(summary = "Export clients with the same filters as the list (capped at 5000 rows)")
    public ResponseEntity<byte[]> clients(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Boolean active) {
        return file("clients.xls", exportService.exportClients(q, active));
    }

    @GetMapping("/appointments.xls")
    @Operation(summary = "Export appointments, including plate and phone (capped at 5000 rows)")
    public ResponseEntity<byte[]> appointments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return file("appointments.xls", exportService.exportAppointments(q, status, from, to));
    }

    @GetMapping("/invoices.xls")
    @Operation(summary = "Export invoices with the same filters as the list (capped at 5000 rows)")
    public ResponseEntity<byte[]> invoices(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) InvoiceStatus status) {
        return file("invoices.xls", exportService.exportInvoices(q, status));
    }

    private ResponseEntity<byte[]> file(String name, byte[] content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .contentType(EXCEL)
                .body(content);
    }
}
