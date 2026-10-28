package com.garage.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.AbstractIntegrationTest;
import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Quote;
import com.garage.domain.QuoteStatus;
import com.garage.domain.Vehicle;
import com.garage.service.StatisticsService;
import com.garage.service.VehicleService;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AppointmentRepositoryIT extends AbstractIntegrationTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Autowired ClientRepository clientRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired QuoteRepository quoteRepository;
    @Autowired StatisticsService statisticsService;
    @Autowired VehicleService vehicleService;

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
        save(client, vehicle, ZonedDateTime.of(2031, 3, 25, 14, 0, 0, 0, PARIS), AppointmentStatus.IN_PROGRESS);

        var stats = statisticsService.monthly(2031);
        assertEquals(12, stats.months().size());
        var march = stats.months().get(2);
        assertEquals(1, march.planned());
        assertEquals(1, march.inProgress());
        assertEquals(2, march.completed());
        assertEquals(0, march.cancelled());
        assertEquals(1, stats.months().get(6).cancelled());
        assertEquals(0, stats.months().get(0).planned());
        assertEquals(0, stats.months().get(1).planned());
        assertEquals(0, stats.months().get(6).inProgress());
    }

    @Test
    void overlapQueriesUseTheDurationAndIgnoreFinishedAppointments() {
        Client client = newClient("overlap-it@example.com");
        Vehicle vehicle = newVehicle(client, "YY-111-YY");
        Vehicle otherVehicle = newVehicle(client, "YY-222-YY");
        // 10:00 to 11:30 Paris on a day unused by other tests.
        ZonedDateTime ten = ZonedDateTime.of(2032, 5, 4, 10, 0, 0, 0, PARIS);
        Appointment booked = save(client, vehicle, ten, 90, AppointmentStatus.PLANNED);
        save(client, otherVehicle, ten, 90, AppointmentStatus.CANCELLED);
        save(client, otherVehicle, ten, 90, AppointmentStatus.COMPLETED);
        UUID none = new UUID(0, 0);

        // 11:00 to 12:00 overlaps; 11:30 to 12:30 only touches the end; 08:00 to 10:00 only touches the start.
        assertTrue(appointmentRepository.existsOverlappingForVehicle(vehicle.getId(),
                ten.plusHours(1).toInstant(), ten.plusHours(2).toInstant(), none));
        assertFalse(appointmentRepository.existsOverlappingForVehicle(vehicle.getId(),
                ten.plusMinutes(90).toInstant(), ten.plusMinutes(150).toInstant(), none));
        assertFalse(appointmentRepository.existsOverlappingForVehicle(vehicle.getId(),
                ten.minusHours(2).toInstant(), ten.toInstant(), none));
        // The appointment being edited is excluded.
        assertFalse(appointmentRepository.existsOverlappingForVehicle(vehicle.getId(),
                ten.toInstant(), ten.plusHours(1).toInstant(), booked.getId()));
        // Cancelled and completed appointments do not occupy the agenda.
        assertFalse(appointmentRepository.existsOverlappingForVehicle(otherVehicle.getId(),
                ten.toInstant(), ten.plusHours(1).toInstant(), none));
        assertEquals(1, appointmentRepository.findOverlapping(ten.toInstant(), ten.plusHours(1).toInstant(), none).size());
    }

    @Test
    void vehicleWithPlannedOrInProgressAppointmentIsDetected() {
        Client client = newClient("busy-it@example.com");
        Vehicle vehicle = newVehicle(client, "XX-333-XX");
        save(client, vehicle, ZonedDateTime.of(2033, 1, 10, 9, 0, 0, 0, PARIS), 60, AppointmentStatus.COMPLETED);
        var busy = List.of(AppointmentStatus.PLANNED, AppointmentStatus.IN_PROGRESS);
        assertFalse(appointmentRepository.existsByVehicleIdAndStatusIn(vehicle.getId(), busy));
        save(client, vehicle, ZonedDateTime.of(2033, 1, 11, 9, 0, 0, 0, PARIS), 60, AppointmentStatus.IN_PROGRESS);
        assertTrue(appointmentRepository.existsByVehicleIdAndStatusIn(vehicle.getId(), busy));
    }

    @Test
    void vehicleSearchFiltersOnActiveFlagAndClientName() {
        Client client = newClient("search-it@example.com");
        client.setLastName("Zebulon");
        clientRepository.save(client);
        Vehicle active = newVehicle(client, "WW-111-WW");
        Vehicle inactive = newVehicle(client, "WW-222-WW");
        inactive.setActive(false);
        vehicleRepository.save(inactive);

        var activeOnly = vehicleService.search("zebulon", true, 0, 10);
        assertEquals(1, activeOnly.totalElements());
        assertEquals(active.getId(), activeOnly.content().get(0).id());
        assertEquals(1, vehicleService.search("zebulon", false, 0, 10).totalElements());
        assertEquals(2, vehicleService.search("zebulon", null, 0, 10).totalElements());
        assertEquals(1, vehicleService.listByClient(client.getId()).size());
    }

    @Test
    void acceptedQuotesOfCompletedAppointmentsAreSummedByPeriod() {
        Client client = newClient("revenue-it@example.com");
        Vehicle vehicle = newVehicle(client, "VV-111-VV");
        Appointment done = save(client, vehicle, ZonedDateTime.of(2034, 2, 10, 9, 0, 0, 0, PARIS), 60,
                AppointmentStatus.COMPLETED);
        Appointment planned = save(client, vehicle, ZonedDateTime.of(2034, 2, 11, 9, 0, 0, 0, PARIS), 60,
                AppointmentStatus.PLANNED);
        Appointment refused = save(client, vehicle, ZonedDateTime.of(2034, 2, 12, 9, 0, 0, 0, PARIS), 60,
                AppointmentStatus.COMPLETED);
        saveQuote(done, QuoteStatus.ACCEPTED, "120.50");
        saveQuote(planned, QuoteStatus.ACCEPTED, "999.00");
        saveQuote(refused, QuoteStatus.REFUSED, "500.00");

        var start = ZonedDateTime.of(2034, 2, 1, 0, 0, 0, 0, PARIS).toInstant();
        var end = ZonedDateTime.of(2034, 3, 1, 0, 0, 0, 0, PARIS).toInstant();
        assertEquals(0, new BigDecimal("120.50").compareTo(
                quoteRepository.sumTotals(QuoteStatus.ACCEPTED, AppointmentStatus.COMPLETED, start, end)));
        assertNull(quoteRepository.sumTotals(QuoteStatus.ACCEPTED, AppointmentStatus.COMPLETED,
                end, end.plusSeconds(3600)));
    }

    private void saveQuote(Appointment appointment, QuoteStatus status, String total) {
        Quote quote = new Quote();
        quote.setAppointment(appointment);
        quote.setStatus(status);
        quote.setTotal(new BigDecimal(total));
        quoteRepository.save(quote);
    }

    private Client newClient(String email) {
        Client client = new Client();
        client.setLastName("Test");
        client.setFirstName("It");
        client.setEmail(email);
        return clientRepository.save(client);
    }

    private Vehicle newVehicle(Client client, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setClient(client);
        vehicle.setPlate(plate);
        vehicle.setBrand("Brand");
        vehicle.setModel("Model");
        return vehicleRepository.save(vehicle);
    }

    private Appointment save(Client client, Vehicle vehicle, ZonedDateTime date, AppointmentStatus status) {
        return save(client, vehicle, date, 60, status);
    }

    private Appointment save(Client client, Vehicle vehicle, ZonedDateTime date, int durationMinutes,
                             AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setDurationMinutes(durationMinutes);
        a.setClient(client);
        a.setVehicle(vehicle);
        a.setDate(date.toInstant());
        a.setReason("Reason");
        a.setStatus(status);
        return appointmentRepository.save(a);
    }
}
