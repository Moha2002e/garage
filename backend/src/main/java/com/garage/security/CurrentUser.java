package com.garage.security;

import com.garage.domain.Role;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        if (auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        // Fallback for non-JWT authentications (e.g. mock users in tests): derive the role from authorities.
        Role role = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .filter(name -> java.util.Arrays.stream(Role.values()).anyMatch(r -> r.name().equals(name)))
                .map(Role::valueOf)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("No role"));
        return new AuthUser(new UUID(0, 0), auth.getName(), role, null);
    }

    public boolean isClient() {
        return get().role() == Role.CLIENT;
    }
}
