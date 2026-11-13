package com.garage.mapper;

import com.garage.domain.Invoice;
import com.garage.domain.InvoiceLine;
import com.garage.dto.InvoiceDtos.InvoiceDetailResponse;
import com.garage.dto.InvoiceDtos.InvoiceLineResponse;
import com.garage.dto.InvoiceDtos.InvoiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = MapperSupport.class, imports = MapperSupport.class)
public interface InvoiceMapper {

    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName",
            expression = "java(MapperSupport.join(invoice.getClient().getFirstName(), invoice.getClient().getLastName()))")
    @Mapping(target = "vehicleLabel",
            expression = "java(MapperSupport.join(invoice.getAppointment().getVehicle().getBrand(), invoice.getAppointment().getVehicle().getModel()))")
    @Mapping(target = "appointmentDate", source = "appointment.date")
    InvoiceResponse toResponse(Invoice invoice);

    /** Same fields as {@link #toResponse(Invoice)}, plus the lines in display order. */
    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName",
            expression = "java(MapperSupport.join(invoice.getClient().getFirstName(), invoice.getClient().getLastName()))")
    @Mapping(target = "vehicleLabel",
            expression = "java(MapperSupport.join(invoice.getAppointment().getVehicle().getBrand(), invoice.getAppointment().getVehicle().getModel()))")
    @Mapping(target = "appointmentDate", source = "appointment.date")
    InvoiceDetailResponse toDetail(Invoice invoice);

    InvoiceLineResponse toLineResponse(InvoiceLine line);
}
