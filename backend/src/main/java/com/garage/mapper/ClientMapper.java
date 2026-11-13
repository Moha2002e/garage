package com.garage.mapper;

import com.garage.domain.Client;
import com.garage.dto.ClientDtos.ClientRequest;
import com.garage.dto.ClientDtos.ClientResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = MapperSupport.class)
public interface ClientMapper {

    ClientResponse toResponse(Client client);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "lastName", qualifiedByName = "trim")
    @Mapping(target = "firstName", qualifiedByName = "trim")
    @Mapping(target = "email", qualifiedByName = "normalizeEmail")
    @Mapping(target = "phone", qualifiedByName = "blankToNull")
    Client toEntity(ClientRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "lastName", qualifiedByName = "trim")
    @Mapping(target = "firstName", qualifiedByName = "trim")
    @Mapping(target = "email", qualifiedByName = "normalizeEmail")
    @Mapping(target = "phone", qualifiedByName = "blankToNull")
    void update(ClientRequest request, @MappingTarget Client client);
}
