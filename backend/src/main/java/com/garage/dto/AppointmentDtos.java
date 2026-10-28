package com.garage.dto;

import com.garage.domain.AppointmentStatus;
import com.garage.domain.QuoteStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /** durationMinutes: 15 to 480 by steps of 15 (checked in the service). */
    public record AppointmentRequest(
            @NotNull UUID clientId,
            @NotNull UUID vehicleId,
            @NotNull Instant date,
            @NotNull Integer durationMinutes,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** New start of the appointment; the duration does not change. */
    public record RescheduleRequest(@NotNull Instant date) {
    }

    public record StatusRequest(@NotNull AppointmentStatus status) {
    }

    /** quoteStatus and quoteTotal are null when the appointment has no quote yet. */
    public record AppointmentResponse(
            UUID id, Instant date, String reason, AppointmentStatus status,
            UUID clientId, String clientFirstName, String clientLastName,
            UUID vehicleId, String plate, String brand, String model,
            int durationMinutes, QuoteStatus quoteStatus, BigDecimal quoteTotal) {
    }

    public record MonthStat(int month, long planned, long inProgress, long completed, long cancelled) {
    }

    public record StatisticsResponse(int year, java.util.List<MonthStat> months) {
    }

    /**
     * end = start + durationMinutes; title is the reason of the appointment.
     * progressPercent (0 to 100, computed by the server): elapsed share of [start, end) for IN_PROGRESS,
     * 100 for COMPLETED, 0 otherwise.
     */
    public record CalendarEvent(UUID id, Instant start, Instant end, String title, AppointmentStatus status,
                                String clientName, String vehicleLabel, int durationMinutes, int progressPercent) {
    }

    public record StatusCount(AppointmentStatus status, long count) {
    }

    public record MonthRevenue(int month, BigDecimal revenue) {
    }

    public record BrandCount(String brand, long count) {
    }
}
