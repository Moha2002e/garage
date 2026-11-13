package com.garage.dto;

import com.garage.domain.StockMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PartDtos {

    private PartDtos() {
    }

    /** Create and update. The stock is never sent here: it only changes through a stock movement. */
    public record PartRequest(
            @NotBlank @Size(max = 40) String reference,
            @NotBlank @Size(max = 255) String name,
            @NotNull @PositiveOrZero BigDecimal unitPrice,
            @PositiveOrZero int lowStockThreshold,
            @PositiveOrZero int leadTimeDays) {
    }

    public record PartResponse(
            UUID id, String reference, String name, BigDecimal unitPrice, int quantityInStock,
            int lowStockThreshold, int leadTimeDays, boolean lowStock, boolean active, Instant createdAt) {
    }

    /** IN and OUT: strictly positive quantity. ADJUSTMENT: signed difference, never zero (checked by the service). */
    public record StockAdjustRequest(
            @NotNull StockMovementType type,
            int quantity,
            @Size(max = 255) String reason) {
    }

    public record StockMovementResponse(
            UUID id, StockMovementType type, int quantity, String reason, Instant createdAt) {
    }
}
