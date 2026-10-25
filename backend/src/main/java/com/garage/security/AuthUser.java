package com.garage.security;

import com.garage.domain.Role;
import java.util.UUID;

/** Authenticated principal: identity, role, and the client record linked to the account (if any). */
public record AuthUser(UUID id, String email, Role role, UUID clientId) {
}
