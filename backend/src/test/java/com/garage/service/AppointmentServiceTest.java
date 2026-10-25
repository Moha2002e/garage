package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Role;
import com.garage.domain.Vehicle;
import com.garage.dto.AppointmentDtos.AppointmentRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.NotFoundException;
import com.garage.repository.AppointmentRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock ClientService clientService;
    @Mock VehicleService vehicleService;
    @Mock CurrentUser currentUser;

    AppointmentService service;
    Client client;
    Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, clientService, vehicleService, currentUser);
        client = new Client();
        client.setId(UUID.randomUUID());
        client.setActive(true);
        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setClient(client);
        when(clientService.find(client.getId())).thenReturn(client);
        when(vehicleService.find(vehicle.getId())).thenReturn(vehicle);
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "e", Role.EMPLOYEE, null));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private AppointmentRequest request(Instant date) {
        return new AppointmentRequest(client.getId(), vehicle.getId(), date, "Oil change");
    }

    @Test
    void createsPlannedAppointmentInTheFuture() {
        var response = service.create(request(Instant.now().plus(1, ChronoUnit.DAYS)));
        assertEquals(AppointmentStatus.PLANNED, response.status());
    }

    @Test
    void rejectsPastDate() {
        assertThrows(BusinessException.class, () -> service.create(request(Instant.now().minus(1, ChronoUnit.HOURS))));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveClient() {
        client.setActive(false);
        assertThrows(BusinessException.class, () -> service.create(request(Instant.now().plus(1, ChronoUnit.DAYS))));
    }

    @Test
    void rejectsVehicleOfAnotherClient() {
        Client other = new Client();
        other.setId(UUID.randomUUID());
        vehicle.setClient(other);
        assertThrows(BusinessException.class, () -> service.create(request(Instant.now().plus(1, ChronoUnit.DAYS))));
    }

    @Test
    void completedAppointmentCannotBeCancelled() {
        Appointment appointment = existing(AppointmentStatus.COMPLETED);
        assertThrows(BusinessException.class, () -> service.changeStatus(appointment.getId(), AppointmentStatus.CANCELLED));
    }

    @Test
    void plannedAppointmentCanBeCompleted() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        assertEquals(AppointmentStatus.COMPLETED,
                service.changeStatus(appointment.getId(), AppointmentStatus.COMPLETED).status());
    }

    @Test
    void clientCannotReadAnotherClientsAppointment() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> service.get(appointment.getId()));
    }

    @Test
    void clientCanReadOwnAppointment() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, client.getId()));
        assertEquals(appointment.getId(), service.get(appointment.getId()).id());
    }

    @Test
    void unlinkedClientAccountSeesNothing() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, null));
        assertEquals(0, service.search(null, null, null, null, 0, 10).totalElements());
        verify(appointmentRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }

    private Appointment existing(AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setClient(client);
        appointment.setVehicle(vehicle);
        appointment.setDate(Instant.now().plus(1, ChronoUnit.DAYS));
        appointment.setReason("Brakes");
        appointment.setStatus(status);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        return appointment;
    }
}
