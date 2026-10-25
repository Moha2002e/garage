package com.garage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class VehicleDtos {

    private VehicleDtos() {
    }

    /** The plate is validated and normalized (AA-123-AA) in the service. */
    public record VehicleRequest(
            @NotNull UUID clientId,
            @NotBlank @Size(max = 20) String plate,
            @NotBlank @Size(max = 100) String brand,
            @NotBlank @Size(max = 100) String model) {
    }

    public record VehicleResponse(UUID id, UUID clientId, String plate, String brand, String model) {
    }
}
