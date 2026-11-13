package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.Appointment;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceLine;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Vehicle;
import com.garage.dto.InvoiceDtos.InvoiceDetailResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.garage.mapper.InvoiceMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceTest {

    @Mock InvoiceRepository invoiceRepository;

    InvoiceService service;
    Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, Mappers.getMapper(InvoiceMapper.class));
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setClient(client);
        appointment.setVehicle(vehicle);
        appointment.setDate(Instant.parse("2026-10-20T12:30:00Z"));
        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setNumber("FAC-2026-000001");
        invoice.setAppointment(appointment);
        invoice.setClient(client);
        invoice.setTotal(new BigDecimal("91.00"));
        invoice.setIssuedAt(Instant.parse("2026-10-21T08:00:00Z"));
        InvoiceLine line = new InvoiceLine();
        line.setInvoice(invoice);
        line.setDescription("Brake pads");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("45.50"));
        line.setTotal(new BigDecimal("91.00"));
        invoice.getLines().add(line);
        when(invoiceRepository.findWithDetailsById(invoice.getId())).thenReturn(Optional.of(invoice));
    }

    @Test
    void detailCarriesTheListFieldsAndTheLines() {
        InvoiceDetailResponse detail = service.get(invoice.getId());

        assertEquals("FAC-2026-000001", detail.number());
        assertEquals(InvoiceStatus.ISSUED, detail.status());
        assertEquals("Jean Dupont", detail.clientName());
        assertEquals("Renault Clio", detail.vehicleLabel());
        assertEquals(Instant.parse("2026-10-20T12:30:00Z"), detail.appointmentDate());
        assertEquals(new BigDecimal("91.00"), detail.total());
        assertNull(detail.paidAt());
        assertEquals(1, detail.lines().size());
        assertEquals("Brake pads", detail.lines().get(0).description());
        assertEquals(new BigDecimal("45.50"), detail.lines().get(0).unitPrice());
        assertEquals(new BigDecimal("91.00"), detail.lines().get(0).total());
    }

    @Test
    void unknownInvoiceIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.get(UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> service.pay(UUID.randomUUID()));
    }

    @Test
    void payMarksTheInvoicePaidWithItsDate() {
        InvoiceDetailResponse paid = service.pay(invoice.getId());

        assertEquals(InvoiceStatus.PAID, paid.status());
        assertNotNull(paid.paidAt());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void payingTwiceIsAConflictAndKeepsTheFirstPaymentDate() {
        service.pay(invoice.getId());
        Instant firstPaidAt = invoice.getPaidAt();

        assertThrows(ConflictException.class, () -> service.pay(invoice.getId()));

        assertEquals(firstPaidAt, invoice.getPaidAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchTextIsLimitedTo100CharactersAndSizesToTenOrTwentyFive() {
        when(invoiceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(invoice)));

        assertThrows(BusinessException.class, () -> service.search("a".repeat(101), null, 0, 10));

        var page = service.search("a".repeat(100), InvoiceStatus.ISSUED, 0, 7);
        assertEquals(1, page.content().size());
        assertEquals("FAC-2026-000001", page.content().get(0).number());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(invoiceRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(10, pageable.getValue().getPageSize());

        service.search(null, null, 2, 25);
        verify(invoiceRepository, org.mockito.Mockito.times(2)).findAll(any(Specification.class), pageable.capture());
        assertEquals(25, pageable.getValue().getPageSize());
        assertEquals(2, pageable.getValue().getPageNumber());
    }
}
