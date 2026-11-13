package com.garage.mapper;

import com.garage.domain.User;
import com.garage.dto.UserDtos.CreateUserRequest;
import com.garage.dto.UserDtos.UpdateUserRequest;
import com.garage.dto.UserDtos.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * The password never goes through a mapper: the service hashes it and passes the hash in, so the clear
 * text cannot end up in an entity by mistake. UserResponse has no password field.
 */
@Mapper(uses = MapperSupport.class)
public interface UserMapper {

    @Mapping(target = "clientId", source = "client.id")
    UserResponse toResponse(User user);

    /** Account created by an administrator: enabled, language left at its default. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "language", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "tokenVersion", ignore = true)
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "email", source = "request.email", qualifiedByName = "normalizeEmail")
    @Mapping(target = "firstName", source = "request.firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "request.lastName", qualifiedByName = "trim")
    @Mapping(target = "role", source = "request.role")
    User toEntity(CreateUserRequest request, String passwordHash);

    /** E-mail, password hash, language and client link are not editable here (the client link is a business rule). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "language", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "tokenVersion", ignore = true)
    @Mapping(target = "firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", qualifiedByName = "trim")
    void update(UpdateUserRequest request, @MappingTarget User user);
}
