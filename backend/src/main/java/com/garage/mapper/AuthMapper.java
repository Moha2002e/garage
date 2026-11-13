package com.garage.mapper;

import com.garage.domain.Language;
import com.garage.domain.PasswordResetToken;
import com.garage.domain.Role;
import com.garage.domain.User;
import com.garage.dto.AuthDtos.MeResponse;
import com.garage.dto.AuthDtos.RegisterRequest;
import com.garage.security.AuthUser;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Authentication-related mappings. Passwords and token hashes are computed by the callers and passed in as
 * already hashed values; nothing here hashes, compares or reads a secret.
 */
@Mapper(uses = MapperSupport.class, imports = {Role.class, Language.class})
public interface AuthMapper {

    /** Free sign-up always creates the least privileged role (CLIENT). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "tokenVersion", ignore = true)
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "email", source = "request.email", qualifiedByName = "normalizeEmail")
    @Mapping(target = "firstName", source = "request.firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "request.lastName", qualifiedByName = "trim")
    @Mapping(target = "role", expression = "java(Role.CLIENT)")
    @Mapping(target = "language",
            expression = "java(request.language() == null ? Language.FR : request.language())")
    User toUser(RegisterRequest request, String passwordHash);

    /** First administrator, created at start-up from the configured e-mail and an already hashed password. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "tokenVersion", ignore = true)
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "email", source = "email", qualifiedByName = "normalizeEmail")
    @Mapping(target = "firstName", constant = "Admin")
    @Mapping(target = "lastName", constant = "Garage")
    @Mapping(target = "role", expression = "java(Role.ADMIN)")
    @Mapping(target = "language", expression = "java(Language.FR)")
    User toAdmin(String email, String passwordHash);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "used", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "tokenHash", source = "tokenHash")
    @Mapping(target = "expiresAt", source = "expiresAt")
    PasswordResetToken toResetToken(User user, String tokenHash, Instant expiresAt);

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientLinked", expression = "java(user.getClient() != null)")
    MeResponse toMe(User user);
}
