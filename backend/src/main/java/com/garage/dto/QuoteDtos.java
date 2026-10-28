package com.garage.dto;

import com.garage.domain.QuoteStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    public record QuoteLineRequest(
            @NotBlank @Size(max = 255) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }

    /** 1 to 50 lines. */
    public record QuoteRequest(@NotEmpty @Size(max = 50) List<@Valid @NotNull QuoteLineRequest> lines) {
    }

    /** Only ACCEPTED or REFUSED are accepted as a target (checked in the service). */
    public record QuoteStatusRequest(@NotNull QuoteStatus status) {
    }

    public record QuoteLineResponse(UUID id, String description, BigDecimal quantity, BigDecimal unitPrice,
                                    BigDecimal lineTotal) {
    }

    public record QuoteResponse(UUID id, UUID appointmentId, QuoteStatus status, List<QuoteLineResponse> lines,
                                BigDecimal total, Instant updatedAt) {
    }
}
