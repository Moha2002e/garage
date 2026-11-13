package com.garage.mapper;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Quote;
import com.garage.domain.Vehicle;
import com.garage.dto.AppointmentDtos.AppointmentRequest;
import com.garage.dto.AppointmentDtos.AppointmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = MapperSupport.class, imports = AppointmentStatus.class)
public interface AppointmentMapper {

    /** quote may be null: quoteStatus and quoteTotal are then null. */
    @Mapping(target = "id", source = "appointment.id")
    @Mapping(target = "date", source = "appointment.date")
    @Mapping(target = "reason", source = "appointment.reason")
    @Mapping(target = "status", source = "appointment.status")
    @Mapping(target = "clientId", source = "appointment.client.id")
    @Mapping(target = "clientFirstName", source = "appointment.client.firstName")
    @Mapping(target = "clientLastName", source = "appointment.client.lastName")
    @Mapping(target = "vehicleId", source = "appointment.vehicle.id")
    @Mapping(target = "plate", source = "appointment.vehicle.plate")
    @Mapping(target = "brand", source = "appointment.vehicle.brand")
    @Mapping(target = "model", source = "appointment.vehicle.model")
    @Mapping(target = "durationMinutes", source = "appointment.durationMinutes")
    @Mapping(target = "quoteStatus", source = "quote.status")
    @Mapping(target = "quoteTotal", source = "quote.total")
    AppointmentResponse toResponse(Appointment appointment, Quote quote);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "client", source = "client")
    @Mapping(target = "vehicle", source = "vehicle")
    @Mapping(target = "date", source = "request.date")
    @Mapping(target = "durationMinutes", source = "request.durationMinutes")
    @Mapping(target = "reason", source = "request.reason", qualifiedByName = "trim")
    @Mapping(target = "status", expression = "java(AppointmentStatus.PLANNED)")
    Appointment toEntity(AppointmentRequest request, Client client, Vehicle vehicle);

    /** Client and status never change through an update. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "vehicle", source = "vehicle")
    @Mapping(target = "date", source = "request.date")
    @Mapping(target = "durationMinutes", source = "request.durationMinutes")
    @Mapping(target = "reason", source = "request.reason", qualifiedByName = "trim")
    void update(AppointmentRequest request, Vehicle vehicle, @MappingTarget Appointment appointment);
}
