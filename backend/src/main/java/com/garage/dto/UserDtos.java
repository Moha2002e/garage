package com.garage.dto;

import com.garage.domain.Language;
import com.garage.domain.Role;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            UUID id, String email, String firstName, String lastName, Role role, Language language,
            boolean enabled, UUID clientId) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    /** A null clientId unlinks the account. */
    public record LinkClientRequest(UUID clientId) {
    }
}
