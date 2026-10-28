package com.garage.dto;

import com.garage.domain.Language;
import com.garage.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            UUID id, String email, String firstName, String lastName, Role role, Language language,
            boolean enabled, UUID clientId) {
    }

    /** Account created by an administrator. Password: 12 to 72 characters with an upper case, a lower case and a digit. */
    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\d).*$",
                    message = "must contain an upper case letter, a lower case letter and a digit") String password,
            @NotNull Role role) {
    }

    /** E-mail and password are not editable here. */
    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull Role role,
            @NotNull Boolean enabled) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    /** A null clientId unlinks the account. */
    public record LinkClientRequest(UUID clientId) {
    }
}
