package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Role;
import com.garage.domain.Vehicle;
import com.garage.dto.VehicleDtos.VehicleRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.VehicleRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.garage.mapper.VehicleMapper;
import org.mapstruct.factory.Mappers;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleServiceTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock ClientService clientService;
    @Mock CurrentUser currentUser;

    VehicleService service;
    Client client;
    Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new VehicleService(vehicleRepository, appointmentRepository, clientService, currentUser,
                Mappers.getMapper(VehicleMapper.class));
        client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName("Ana");
        client.setLastName("Martin");
        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setClient(client);
        vehicle.setPlate("AB-123-CD");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        when(vehicleRepository.findWithClientById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(clientService.find(client.getId())).thenReturn(client);
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "e", Role.EMPLOYEE, null));
    }

    @Test
    void newVehicleIsActive() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));
        var response = service.create(new VehicleRequest(client.getId(), "ab 123 cd", "Peugeot", "208"));
        assertTrue(response.active());
        assertEquals("AB-123-CD", response.plate());
        assertEquals("Martin", response.clientLastName());
    }

    @Test
    void deactivationIsRefusedWithPlannedOrInProgressAppointments() {
        when(appointmentRepository.existsByVehicleIdAndStatusIn(vehicle.getId(),
                List.of(AppointmentStatus.PLANNED, AppointmentStatus.IN_PROGRESS))).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.deactivate(vehicle.getId()));
        assertTrue(vehicle.isActive());
    }

    @Test
    void deactivationKeepsTheRowAndOnlyClearsTheFlag() {
        service.deactivate(vehicle.getId());
        assertFalse(vehicle.isActive());
        verify(vehicleRepository, never()).delete(any(com.garage.domain.Vehicle.class));
    }

    @Test
    void reactivationSetsTheVehicleActiveAgain() {
        vehicle.setActive(false);
        var response = service.reactivate(vehicle.getId());
        assertTrue(response.active());
        assertTrue(vehicle.isActive());
    }

    @Test
    void reactivatingAnUnknownVehicleIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.reactivate(UUID.randomUUID()));
    }

    @Test
    void clientVehicleListOnlyAsksForActiveVehicles() {
        when(vehicleRepository.findByClientIdAndActiveTrueOrderByPlate(client.getId())).thenReturn(List.of(vehicle));
        assertEquals(1, service.listByClient(client.getId()).size());
        verify(vehicleRepository).findByClientIdAndActiveTrueOrderByPlate(client.getId());
    }

    @Test
    void invalidPlateMessageDoesNotEchoThePlate() {
        var ex = assertThrows(BusinessException.class,
                () -> service.create(new VehicleRequest(client.getId(), "secret-plate", "A", "B")));
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void clientVehicleListWithoutFilterReturnsInactiveVehiclesToo() {
        Vehicle inactive = new Vehicle();
        inactive.setId(UUID.randomUUID());
        inactive.setClient(client);
        inactive.setPlate("ZZ-999-ZZ");
        inactive.setActive(false);
        when(vehicleRepository.findByClientIdOrderByPlate(client.getId())).thenReturn(List.of(vehicle, inactive));
        var list = service.listByClient(client.getId(), null);
        assertEquals(2, list.size());
        assertFalse(list.get(1).active());
        verify(vehicleRepository, never()).findByClientIdAndActiveTrueOrderByPlate(any());
    }

    @Test
    void vehicleDetailCarriesTheClientIdentity() {
        var response = service.get(vehicle.getId());
        assertEquals(client.getId(), response.clientId());
        assertEquals("Ana", response.clientFirstName());
        assertEquals("Martin", response.clientLastName());
    }

    @Test
    void aClientSeesTheirOwnVehicle() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, client.getId()));
        assertEquals(vehicle.getId(), service.get(vehicle.getId()).id());
    }

    @Test
    void aClientCannotSeeAnotherClientsVehicle() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> service.get(vehicle.getId()));
    }

    @Test
    void aClientWithoutLinkedRecordCannotSeeAnyVehicle() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, null));
        assertThrows(NotFoundException.class, () -> service.get(vehicle.getId()));
    }
}
