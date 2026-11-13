package com.garage.mapper;

import com.garage.domain.Client;
import com.garage.domain.Vehicle;
import com.garage.dto.VehicleDtos.VehicleRequest;
import com.garage.dto.VehicleDtos.VehicleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = MapperSupport.class)
public interface VehicleMapper {

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientFirstName", source = "client.firstName")
    @Mapping(target = "clientLastName", source = "client.lastName")
    VehicleResponse toResponse(Vehicle vehicle);

    /** The plate is validated and normalized by the service before it reaches the mapper. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "client", source = "client")
    @Mapping(target = "plate", source = "normalizedPlate")
    @Mapping(target = "brand", source = "request.brand", qualifiedByName = "trim")
    @Mapping(target = "model", source = "request.model", qualifiedByName = "trim")
    Vehicle toEntity(VehicleRequest request, String normalizedPlate, Client client);

    /** The client of a vehicle never changes. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "plate", source = "normalizedPlate")
    @Mapping(target = "brand", source = "request.brand", qualifiedByName = "trim")
    @Mapping(target = "model", source = "request.model", qualifiedByName = "trim")
    void update(VehicleRequest request, String normalizedPlate, @MappingTarget Vehicle vehicle);
}
