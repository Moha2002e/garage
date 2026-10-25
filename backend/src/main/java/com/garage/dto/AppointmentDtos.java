package com.garage.dto;

import com.garage.domain.AppointmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    public record AppointmentRequest(
            @NotNull UUID clientId,
            @NotNull UUID vehicleId,
            @NotNull Instant date,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record StatusRequest(@NotNull AppointmentStatus status) {
    }

    public record AppointmentResponse(
            UUID id, Instant date, String reason, AppointmentStatus status,
            UUID clientId, String clientFirstName, String clientLastName,
            UUID vehicleId, String plate, String brand, String model) {
    }

    public record MonthStat(int month, long planned, long completed, long cancelled) {
    }

    public record StatisticsResponse(int year, java.util.List<MonthStat> months) {
    }
}
