package com.garage.dto;

import com.garage.domain.Language;
import com.garage.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password,
            boolean rememberMe) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            Language language) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {
    }

    /** clientLinked tells the front-end whether a CLIENT account is attached to a client record yet. */
    public record MeResponse(
            UUID id, String email, String firstName, String lastName, Role role, Language language,
            UUID clientId, boolean clientLinked) {
    }
}
