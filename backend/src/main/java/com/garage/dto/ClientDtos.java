package com.garage.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ClientDtos {

    private ClientDtos() {
    }

    public record ClientRequest(
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = "^[0-9+()\\-. ]{6,30}$", message = "must be a valid phone number") String phone) {
    }

    /** The client model has no address field, so none is returned. */
    public record ClientResponse(
            UUID id, String lastName, String firstName, String email, String phone, boolean active,
            Instant createdAt) {
    }
}
