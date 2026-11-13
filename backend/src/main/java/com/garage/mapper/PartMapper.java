package com.garage.mapper;

import com.garage.domain.Part;
import com.garage.domain.StockMovement;
import com.garage.dto.PartDtos.PartRequest;
import com.garage.dto.PartDtos.PartResponse;
import com.garage.dto.PartDtos.StockMovementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = MapperSupport.class)
public interface PartMapper {

    PartResponse toResponse(Part part);

    StockMovementResponse toResponse(StockMovement movement);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "quantityInStock", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "reference", qualifiedByName = "normalizeReference")
    @Mapping(target = "name", qualifiedByName = "trim")
    Part toEntity(PartRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "quantityInStock", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "reference", qualifiedByName = "normalizeReference")
    @Mapping(target = "name", qualifiedByName = "trim")
    void update(PartRequest request, @MappingTarget Part part);
}
