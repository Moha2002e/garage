package com.garage.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.garage.AbstractIntegrationTest;
import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Vehicle;
import com.garage.service.StatisticsService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AppointmentRepositoryIT extends AbstractIntegrationTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Autowired ClientRepository clientRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired StatisticsService statisticsService;

    @Test
    void monthlyStatisticsUseParisTimeAndFillEmptyMonths() {
        Client client = new Client();
        client.setLastName("Test");
        client.setFirstName("Stats");
        client.setEmail("stats-it@example.com");
        clientRepository.save(client);
        Vehicle vehicle = new Vehicle();
        vehicle.setClient(client);
        vehicle.setPlate("ZZ-999-ZZ");
        vehicle.setBrand("Brand");
        vehicle.setModel("Model");
        vehicleRepository.save(vehicle);

        // Year 2031 is unused by other tests. 00:30 Paris on March 1st is still Feb 28th in UTC.
        save(client, vehicle, ZonedDateTime.of(2031, 3, 1, 0, 30, 0, 0, PARIS), AppointmentStatus.PLANNED);
        save(client, vehicle, ZonedDateTime.of(2031, 3, 15, 10, 0, 0, 0, PARIS), AppointmentStatus.COMPLETED);
        save(client, vehicle, ZonedDateTime.of(2031, 3, 20, 10, 0, 0, 0, PARIS), AppointmentStatus.COMPLETED);
        save(client, vehicle, ZonedDateTime.of(2031, 7, 2, 9, 0, 0, 0, PARIS), AppointmentStatus.CANCELLED);

        var stats = statisticsService.monthly(2031);
        assertEquals(12, stats.months().size());
        var march = stats.months().get(2);
        assertEquals(1, march.planned());
        assertEquals(2, march.completed());
        assertEquals(0, march.cancelled());
        assertEquals(1, stats.months().get(6).cancelled());
        assertEquals(0, stats.months().get(0).planned());
        assertEquals(0, stats.months().get(1).planned());
    }

    private void save(Client client, Vehicle vehicle, ZonedDateTime date, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setClient(client);
        a.setVehicle(vehicle);
        a.setDate(date.toInstant());
        a.setReason("Reason");
        a.setStatus(status);
        appointmentRepository.save(a);
    }
}
