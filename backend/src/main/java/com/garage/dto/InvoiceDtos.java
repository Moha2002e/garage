package com.garage.dto;

import com.garage.domain.InvoiceErrorCode;
import com.garage.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    /**
     * clientName is "First Last", vehicleLabel is "Brand Model" (no plate), appointmentDate is the start of the
     * invoiced appointment. paidAt is null while the invoice is ISSUED.
     */
    public record InvoiceResponse(UUID id, String number, InvoiceStatus status, UUID appointmentId, UUID clientId,
                                  String clientName, String vehicleLabel, Instant appointmentDate,
                                  BigDecimal total, Instant issuedAt, Instant paidAt) {
    }

    public record InvoiceLineResponse(String description, BigDecimal quantity, BigDecimal unitPrice,
                                      BigDecimal total) {
    }

    /** Same fields as {@link InvoiceResponse}, plus the lines in display order. */
    public record InvoiceDetailResponse(UUID id, String number, InvoiceStatus status, UUID appointmentId,
                                        UUID clientId, String clientName, String vehicleLabel,
                                        Instant appointmentDate, BigDecimal total, Instant issuedAt, Instant paidAt,
                                        List<InvoiceLineResponse> lines) {
    }

    /**
     * One run of the invoice job. id is the Spring Batch job execution id; status is its BatchStatus name
     * (COMPLETED, FAILED, STARTED...). skipCount counts the ignored appointments, errorCount the rows of
     * batch_errors of this run. endTime is null while the run is not finished.
     */
    public record BatchRunResponse(long id, String status, long readCount, long writeCount, long skipCount,
                                   long errorCount, Instant startTime, Instant endTime) {
    }

    /**
     * appointmentLabel is "dd/MM/yyyy HH:mm, Client Name, Brand Model" (Europe/Paris), or null when the error is
     * not tied to an appointment or the appointment cannot be found.
     */
    public record BatchErrorResponse(UUID id, long runId, UUID appointmentId, String appointmentLabel,
                                     InvoiceErrorCode code, String message, Instant occurredAt) {
    }
}
