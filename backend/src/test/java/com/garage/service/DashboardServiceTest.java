package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.QuoteStatus;
import com.garage.domain.Vehicle;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.ClientRepository;
import com.garage.repository.InvoiceRepository;
import com.garage.repository.QuoteRepository;
import com.garage.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock AppointmentService appointmentService;
    @Mock QuoteRepository quoteRepository;
    @Mock ClientRepository clientRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock com.garage.repository.PartRepository partRepository;

    @Test
    void assemblesTheKeyFigures() {
        var service = new DashboardService(appointmentRepository, appointmentService, quoteRepository,
                clientRepository, vehicleRepository, invoiceRepository, partRepository);
        when(appointmentRepository.findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(appointmentService.toResponses(List.of())).thenReturn(List.of());
        when(appointmentRepository.countByStatusAndDateGreaterThanEqualAndDateLessThan(eq(AppointmentStatus.PLANNED),
                any(), any())).thenReturn(4L);
        when(appointmentRepository.countByStatusAndDateGreaterThanEqualAndDateLessThan(eq(AppointmentStatus.COMPLETED),
                any(), any())).thenReturn(7L);
        when(appointmentRepository.countByStatus(AppointmentStatus.IN_PROGRESS)).thenReturn(2L);
        when(clientRepository.countByActive(true)).thenReturn(30L);
        when(vehicleRepository.countByActive(true)).thenReturn(41L);
        when(quoteRepository.sumTotals(eq(QuoteStatus.ACCEPTED), eq(AppointmentStatus.COMPLETED), any(), any()))
                .thenReturn(new BigDecimal("1250.5"));

        var dashboard = service.get();

        assertEquals(4, dashboard.plannedNext7Days());
        assertEquals(2, dashboard.inProgress());
        assertEquals(30, dashboard.activeClients());
        assertEquals(41, dashboard.activeVehicles());
        assertEquals(7, dashboard.monthCompleted());
        assertEquals(new BigDecimal("1250.50"), dashboard.monthRevenue());
    }

    @Test
    void revenueIsZeroWithTwoDecimalsWhenThereIsNoAcceptedQuote() {
        var service = new DashboardService(appointmentRepository, appointmentService, quoteRepository,
                clientRepository, vehicleRepository, invoiceRepository, partRepository);
        when(appointmentRepository.findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(appointmentService.toResponses(List.of())).thenReturn(List.of());
        when(quoteRepository.sumTotals(any(), any(), any(), any())).thenReturn(null);
        assertEquals(new BigDecimal("0.00"), service.get().monthRevenue());
    }

    private static Appointment appointment(AppointmentStatus status, String start, int minutes) {
        Client client = new Client();
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Appointment a = new Appointment();
        a.setId(UUID.randomUUID());
        a.setClient(client);
        a.setVehicle(vehicle);
        a.setStatus(status);
        a.setDate(Instant.parse(start));
        a.setDurationMinutes(minutes);
        return a;
    }

    @Test
    void shortListsAndInvoiceFiguresAreAddedWithoutChangingTheExistingFields() {
        var service = new DashboardService(appointmentRepository, appointmentService, quoteRepository,
                clientRepository, vehicleRepository, invoiceRepository, partRepository);
        Appointment today = appointment(AppointmentStatus.COMPLETED, "2026-10-25T08:00:00Z", 90);
        Appointment running = appointment(AppointmentStatus.IN_PROGRESS, "2026-10-25T09:00:00Z", 60);
        Appointment next = appointment(AppointmentStatus.PLANNED, "2026-10-26T09:00:00Z", 30);
        when(appointmentRepository.findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any(), any()))
                .thenReturn(List.of(today));
        when(appointmentService.toResponses(any())).thenReturn(List.of());
        when(appointmentRepository.findTop20ByStatusOrderByDateAsc(AppointmentStatus.IN_PROGRESS))
                .thenReturn(List.of(running));
        when(appointmentRepository.findTop5ByStatusAndDateGreaterThanEqualOrderByDateAsc(eq(AppointmentStatus.PLANNED), any()))
                .thenReturn(List.of(next));
        when(invoiceRepository.countByStatus(InvoiceStatus.ISSUED)).thenReturn(3L);
        when(invoiceRepository.sumTotalByStatus(InvoiceStatus.ISSUED)).thenReturn(new BigDecimal("420.5"));
        when(invoiceRepository.countInvoiceable()).thenReturn(2L);
        when(quoteRepository.sumTotals(any(), any(), any(), any())).thenReturn(new BigDecimal("100"));

        var dashboard = service.get();

        assertEquals(new BigDecimal("100.00"), dashboard.monthRevenue());
        assertEquals(1, dashboard.todayAppointments().size());
        var shortToday = dashboard.todayAppointments().get(0);
        assertEquals(today.getId(), shortToday.id());
        assertEquals(Instant.parse("2026-10-25T08:00:00Z"), shortToday.start());
        assertEquals(Instant.parse("2026-10-25T09:30:00Z"), shortToday.end());
        assertEquals(AppointmentStatus.COMPLETED, shortToday.status());
        assertEquals("Jean Dupont", shortToday.clientName());
        assertEquals("Renault Clio", shortToday.vehicleLabel());
        assertEquals(running.getId(), dashboard.inProgressNow().get(0).id());
        assertEquals(next.getId(), dashboard.upcoming().get(0).id());
        assertEquals(3, dashboard.unpaidInvoicesCount());
        assertEquals(new BigDecimal("420.50"), dashboard.unpaidInvoicesAmount());
        assertEquals(2, dashboard.pendingInvoiceCount());
        // Today's short list holds every status except CANCELLED.
        verify(appointmentRepository).findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                eq(List.of(AppointmentStatus.PLANNED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED)),
                any(), any());
    }

    @Test
    void unpaidAmountIsZeroWithTwoDecimalsWithoutIssuedInvoice() {
        var service = new DashboardService(appointmentRepository, appointmentService, quoteRepository,
                clientRepository, vehicleRepository, invoiceRepository, partRepository);
        when(appointmentRepository.findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(appointmentService.toResponses(any())).thenReturn(List.of());
        when(invoiceRepository.sumTotalByStatus(InvoiceStatus.ISSUED)).thenReturn(null);

        var dashboard = service.get();

        assertEquals(new BigDecimal("0.00"), dashboard.unpaidInvoicesAmount());
        assertEquals(0, dashboard.unpaidInvoicesCount());
        assertEquals(0, dashboard.pendingInvoiceCount());
        assertEquals(List.of(), dashboard.upcoming());
        assertEquals(List.of(), dashboard.inProgressNow());
    }
}
