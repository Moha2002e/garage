package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.config.AppProperties;
import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Role;
import com.garage.domain.Vehicle;
import com.garage.dto.AppointmentDtos.AppointmentRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.QuoteRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.garage.mapper.AppointmentMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock ClientService clientService;
    @Mock QuoteRepository quoteRepository;
    @Mock VehicleService vehicleService;
    @Mock CurrentUser currentUser;

    @Mock QuoteStockService quoteStockService;

    AppointmentService service;
    Client client;
    Vehicle vehicle;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(null, null, null, null, null, 0, 0, null,
                new AppProperties.Garage(2, null), null);
        service = new AppointmentService(appointmentRepository, quoteRepository, clientService, vehicleService,
                currentUser, props, Mappers.getMapper(AppointmentMapper.class), quoteStockService);
        client = new Client();
        client.setId(UUID.randomUUID());
        client.setActive(true);
        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setClient(client);
        vehicle.setActive(true);
        when(clientService.find(client.getId())).thenReturn(client);
        when(vehicleService.find(vehicle.getId())).thenReturn(vehicle);
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "e", Role.EMPLOYEE, null));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
        when(appointmentRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());
        when(appointmentRepository.existsOverlappingForVehicle(any(), any(), any(), any())).thenReturn(false);
    }

    private AppointmentRequest request(Instant date) {
        return request(date, 60);
    }

    private AppointmentRequest request(Instant date, int durationMinutes) {
        return new AppointmentRequest(client.getId(), vehicle.getId(), date, durationMinutes, "Oil change");
    }

    private Appointment other(Instant start, int durationMinutes) {
        Appointment a = new Appointment();
        a.setId(UUID.randomUUID());
        a.setDate(start);
        a.setDurationMinutes(durationMinutes);
        a.setStatus(AppointmentStatus.PLANNED);
        return a;
    }

    @Test
    void createsPlannedAppointmentInTheFuture() {
        var response = service.create(request(Instant.now().plus(1, ChronoUnit.DAYS)));
        assertEquals(AppointmentStatus.PLANNED, response.status());
    }

    @Test
    void acceptsPastDateToEnterHistory() {
        var response = service.create(request(Instant.now().minus(30, ChronoUnit.DAYS)));
        assertEquals(AppointmentStatus.PLANNED, response.status());
        assertEquals(60, response.durationMinutes());
        assertNull(response.quoteStatus());
        assertNull(response.quoteTotal());
    }

    @Test
    void rejectsDurationOutsideRangeOrStep() {
        Instant date = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThrows(BusinessException.class, () -> service.create(request(date, 0)));
        assertThrows(BusinessException.class, () -> service.create(request(date, 10)));
        assertThrows(BusinessException.class, () -> service.create(request(date, 20)));
        assertThrows(BusinessException.class, () -> service.create(request(date, 495)));
        assertThrows(BusinessException.class, () -> service.create(request(date, 500)));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void acceptsMinimumAndMaximumDuration() {
        Instant date = Instant.now().plus(1, ChronoUnit.DAYS);
        assertEquals(15, service.create(request(date, 15)).durationMinutes());
        assertEquals(480, service.create(request(date, 480)).durationMinutes());
    }

    @Test
    void rejectsInactiveVehicle() {
        vehicle.setActive(false);
        assertThrows(BusinessException.class, () -> service.create(request(Instant.now().plus(1, ChronoUnit.DAYS))));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectsSameVehicleOverlap() {
        when(appointmentRepository.existsOverlappingForVehicle(eq(vehicle.getId()), any(), any(), any()))
                .thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(request(Instant.now().plus(1, ChronoUnit.DAYS))));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectsWhenCapacityIsReached() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        // Capacity is 2: two appointments already run at the same time as the new one.
        when(appointmentRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(other(start, 60), other(start.plus(15, ChronoUnit.MINUTES), 60)));
        assertThrows(ConflictException.class, () -> service.create(request(start.plus(20, ChronoUnit.MINUTES))));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void acceptsWhenOverlappingAppointmentsDoNotRunTogether() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        // Both overlap the new slot (10:00 to 12:00) but never at the same moment: peak is 1, capacity is 2.
        when(appointmentRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(other(start.minus(30, ChronoUnit.MINUTES), 60),
                        other(start.plus(60, ChronoUnit.MINUTES), 60)));
        assertEquals(AppointmentStatus.PLANNED, service.create(request(start, 120)).status());
    }

    @Test
    void peakConcurrencyCountsOnlySimultaneousAppointments() {
        Instant s = Instant.parse("2030-01-01T10:00:00Z");
        Instant e = s.plus(2, ChronoUnit.HOURS);
        assertEquals(0, AppointmentService.peakConcurrency(s, e, List.of()));
        // Touching intervals do not overlap: [09:00, 10:00) and [12:00, 13:00).
        assertEquals(0, AppointmentService.peakConcurrency(s, e,
                List.of(other(s.minus(1, ChronoUnit.HOURS), 60), other(e, 60))));
        assertEquals(2, AppointmentService.peakConcurrency(s, e,
                List.of(other(s, 60), other(s.plus(30, ChronoUnit.MINUTES), 60))));
        assertEquals(1, AppointmentService.peakConcurrency(s, e,
                List.of(other(s, 30), other(s.plus(30, ChronoUnit.MINUTES), 30))));
    }

    @Test
    void updateExcludesTheEditedAppointmentFromConflicts() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        service.update(appointment.getId(), request(appointment.getDate(), 90));
        verify(appointmentRepository).existsOverlappingForVehicle(eq(vehicle.getId()), any(), any(),
                eq(appointment.getId()));
        verify(appointmentRepository).findOverlapping(any(), any(), eq(appointment.getId()));
        assertEquals(90, appointment.getDurationMinutes());
    }

    @Test
    void updateIsRefusedWhenNotPlanned() {
        Appointment appointment = existing(AppointmentStatus.IN_PROGRESS);
        assertThrows(BusinessException.class, () -> service.update(appointment.getId(), request(Instant.now())));
    }

    @Test
    void updateRejectsOverlapWithAnotherAppointment() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        when(appointmentRepository.existsOverlappingForVehicle(any(), any(), any(), eq(appointment.getId())))
                .thenReturn(true);
        assertThrows(ConflictException.class, () -> service.update(appointment.getId(), request(appointment.getDate())));
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
    void plannedAppointmentCanStartThenComplete() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        assertEquals(AppointmentStatus.IN_PROGRESS,
                service.changeStatus(appointment.getId(), AppointmentStatus.IN_PROGRESS).status());
        assertEquals(AppointmentStatus.COMPLETED,
                service.changeStatus(appointment.getId(), AppointmentStatus.COMPLETED).status());
    }

    @Test
    void inProgressAppointmentCanBeCancelledButNotPlannedAgain() {
        Appointment appointment = existing(AppointmentStatus.IN_PROGRESS);
        assertThrows(BusinessException.class, () -> service.changeStatus(appointment.getId(), AppointmentStatus.PLANNED));
        assertEquals(AppointmentStatus.CANCELLED,
                service.changeStatus(appointment.getId(), AppointmentStatus.CANCELLED).status());
    }

    @Test
    void cancelledAppointmentCannotStart() {
        Appointment appointment = existing(AppointmentStatus.CANCELLED);
        assertThrows(BusinessException.class, () -> service.changeStatus(appointment.getId(), AppointmentStatus.IN_PROGRESS));
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

    @Test
    void calendarBuildsEventsWithEndAndLabels() {
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Instant from = Instant.parse("2031-03-01T00:00:00Z");
        Appointment a = other(from.plus(2, ChronoUnit.HOURS), 90);
        a.setClient(client);
        a.setVehicle(vehicle);
        a.setReason("Brakes");
        when(appointmentRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any()))
                .thenReturn(List.of(a));

        var events = service.calendar(from, from.plus(7, ChronoUnit.DAYS));

        assertEquals(1, events.size());
        var event = events.get(0);
        assertEquals(a.getId(), event.id());
        assertEquals(a.getDate(), event.start());
        assertEquals(a.getDate().plus(90, ChronoUnit.MINUTES), event.end());
        assertEquals("Brakes", event.title());
        assertEquals(AppointmentStatus.PLANNED, event.status());
        assertEquals("Jean Dupont", event.clientName());
        assertEquals("Renault Clio", event.vehicleLabel());
    }

    @Test
    void calendarDropsAppointmentsThatEndedBeforeTheWindow() {
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Instant from = Instant.parse("2031-03-01T00:00:00Z");
        Appointment ended = other(from.minus(3, ChronoUnit.HOURS), 60);
        Appointment overlapping = other(from.minus(1, ChronoUnit.HOURS), 120);
        for (Appointment a : List.of(ended, overlapping)) {
            a.setClient(client);
            a.setVehicle(vehicle);
            a.setReason("Brakes");
        }
        when(appointmentRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any()))
                .thenReturn(List.of(ended, overlapping));

        var events = service.calendar(from, from.plus(7, ChronoUnit.DAYS));

        assertEquals(1, events.size());
        assertEquals(overlapping.getId(), events.get(0).id());
    }

    @Test
    void calendarRejectsRangeOverOneHundredDaysAndInvalidRange() {
        Instant from = Instant.parse("2031-03-01T00:00:00Z");
        assertThrows(BusinessException.class, () -> service.calendar(from, from.plus(101, ChronoUnit.DAYS)));
        assertThrows(BusinessException.class, () -> service.calendar(from, from));
        assertThrows(BusinessException.class, () -> service.calendar(from, from.minus(1, ChronoUnit.DAYS)));
        verify(appointmentRepository, never()).findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any());
    }

    @Test
    void calendarAcceptsExactlyOneHundredDays() {
        Instant from = Instant.parse("2031-03-01T00:00:00Z");
        when(appointmentRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any()))
                .thenReturn(List.of());
        assertEquals(0, service.calendar(from, from.plus(100, ChronoUnit.DAYS)).size());
    }

    @Test
    void calendarOfAClientOnlyQueriesTheirOwnAppointments() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, client.getId()));
        Instant from = Instant.parse("2031-03-01T00:00:00Z");
        when(appointmentRepository.findByClientIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                eq(client.getId()), any(), any())).thenReturn(List.of());

        assertEquals(0, service.calendar(from, from.plus(7, ChronoUnit.DAYS)).size());

        verify(appointmentRepository).findByClientIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                eq(client.getId()), any(), any());
        verify(appointmentRepository, never()).findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any());
    }

    @Test
    void calendarOfAnUnlinkedClientAccountIsEmpty() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, null));
        Instant from = Instant.parse("2031-03-01T00:00:00Z");
        assertEquals(0, service.calendar(from, from.plus(7, ChronoUnit.DAYS)).size());
        verify(appointmentRepository, never()).findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any());
    }

    @Test
    void clientHistoryIsSortedNewestFirstAndSizeIsCappedAt50() {
        Appointment a = existing(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(a), i.getArgument(1), 1));

        var response = service.listByClient(client.getId(), 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("date").getDirection());
        assertEquals(1, response.content().size());
        assertEquals(a.getId(), response.content().get(0).id());
    }

    @Test
    void historyPageSizeFallsBackToTenWhenInvalid() {
        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(), i.getArgument(1), 0));
        service.listByClient(client.getId(), -3, 0);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(10, captor.getValue().getPageSize());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void historyOfAnUnknownClientIsNotFound() {
        when(clientService.find(any())).thenThrow(new NotFoundException("Client not found"));
        assertThrows(NotFoundException.class, () -> service.listByClient(UUID.randomUUID(), 0, 10));
    }

    @Test
    void aClientCanReadTheirOwnHistory() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, client.getId()));
        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(), i.getArgument(1), 0));
        assertEquals(0, service.listByClient(client.getId(), 0, 10).totalElements());
    }

    @Test
    void aClientCannotReadAnotherClientsHistory() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> service.listByClient(client.getId(), 0, 10));
        verify(appointmentRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void anUnlinkedClientAccountCannotReadAnyClientHistory() {
        when(currentUser.get()).thenReturn(new AuthUser(UUID.randomUUID(), "c", Role.CLIENT, null));
        assertThrows(NotFoundException.class, () -> service.listByClient(client.getId(), 0, 10));
    }

    @Test
    void vehicleHistoryUsesTheAccessCheckOfTheVehicleService() {
        when(vehicleService.findAccessible(vehicle.getId())).thenReturn(vehicle);
        Appointment a = existing(AppointmentStatus.PLANNED);
        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(a), i.getArgument(1), 1));

        var response = service.listByVehicle(vehicle.getId(), 0, 10);

        assertEquals(1, response.totalElements());
        verify(vehicleService).findAccessible(vehicle.getId());
    }

    @Test
    void vehicleHistoryOfAnotherClientsVehicleIsNotFound() {
        when(vehicleService.findAccessible(vehicle.getId())).thenThrow(new NotFoundException("Vehicle not found"));
        assertThrows(NotFoundException.class, () -> service.listByVehicle(vehicle.getId(), 0, 10));
        verify(appointmentRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rescheduleMovesAPlannedAppointmentAndKeepsItsDuration() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        appointment.setDurationMinutes(90);
        Instant target = Instant.parse("2031-05-02T09:30:00Z");

        var response = service.reschedule(appointment.getId(), target);

        assertEquals(target, response.date());
        assertEquals(90, response.durationMinutes());
        assertEquals(target, appointment.getDate());
        verify(appointmentRepository).existsOverlappingForVehicle(eq(vehicle.getId()), eq(target),
                eq(target.plus(90, ChronoUnit.MINUTES)), eq(appointment.getId()));
        verify(appointmentRepository).findOverlapping(eq(target), eq(target.plus(90, ChronoUnit.MINUTES)),
                eq(appointment.getId()));
    }

    @Test
    void rescheduleRejectsVehicleConflictAndCapacityWithoutChangingTheDate() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        Instant original = appointment.getDate();
        Instant target = Instant.parse("2031-05-02T09:30:00Z");
        when(appointmentRepository.existsOverlappingForVehicle(any(), any(), any(), eq(appointment.getId())))
                .thenReturn(true);
        assertThrows(ConflictException.class, () -> service.reschedule(appointment.getId(), target));

        when(appointmentRepository.existsOverlappingForVehicle(any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(other(target, 60), other(target, 60)));
        assertThrows(ConflictException.class, () -> service.reschedule(appointment.getId(), target));
        assertEquals(original, appointment.getDate());
    }

    @Test
    void rescheduleIsRefusedUnlessPlanned() {
        Instant target = Instant.parse("2031-05-02T09:30:00Z");
        for (AppointmentStatus status : List.of(AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED)) {
            Appointment appointment = existing(status);
            assertThrows(BusinessException.class, () -> service.reschedule(appointment.getId(), target));
        }
        verify(appointmentRepository, never()).findOverlapping(any(), any(), any());
    }

    @Test
    void rescheduleRejectsDatesOutsideYears2000To2100AndUnknownAppointment() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        assertThrows(BusinessException.class,
                () -> service.reschedule(appointment.getId(), Instant.parse("1999-12-31T12:00:00Z")));
        assertThrows(BusinessException.class,
                () -> service.reschedule(appointment.getId(), Instant.parse("2101-01-01T12:00:00Z")));
        assertThrows(NotFoundException.class, () -> service.reschedule(UUID.randomUUID(), Instant.now()));
    }

    @Test
    void calendarEventsCarryDurationAndProgress() {
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Instant now = Instant.now();
        Appointment running = other(now.minus(30, ChronoUnit.MINUTES), 120);
        running.setStatus(AppointmentStatus.IN_PROGRESS);
        Appointment done = other(now.minus(5, ChronoUnit.HOURS), 60);
        done.setStatus(AppointmentStatus.COMPLETED);
        Appointment planned = other(now.plus(1, ChronoUnit.HOURS), 60);
        Appointment overdue = other(now.minus(10, ChronoUnit.HOURS), 60);
        overdue.setStatus(AppointmentStatus.IN_PROGRESS);
        for (Appointment a : List.of(running, done, planned, overdue)) {
            a.setClient(client);
            a.setVehicle(vehicle);
            a.setReason("Brakes");
        }

        assertEquals(120, AppointmentService.toCalendarEvent(running, now).durationMinutes());
        assertEquals(25, AppointmentService.toCalendarEvent(running, now).progressPercent());
        assertEquals(100, AppointmentService.toCalendarEvent(done, now).progressPercent());
        assertEquals(0, AppointmentService.toCalendarEvent(planned, now).progressPercent());
        assertEquals(100, AppointmentService.toCalendarEvent(overdue, now).progressPercent());
    }

    @Test
    void calendarIsCappedAt2000Events() {
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Instant from = Instant.parse("2031-03-01T00:00:00Z");
        List<Appointment> many = new java.util.ArrayList<>();
        for (int i = 0; i < 2100; i++) {
            Appointment a = other(from.plus(i, ChronoUnit.MINUTES), 15);
            a.setClient(client);
            a.setVehicle(vehicle);
            a.setReason("Brakes");
            many.add(a);
        }
        when(appointmentRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(any(), any()))
                .thenReturn(many);
        assertEquals(2000, service.calendar(from, from.plus(7, ChronoUnit.DAYS)).size());
    }

    @Test
    void cancellingReleasesTheStockOfItsQuote() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        com.garage.domain.Quote quote = new com.garage.domain.Quote();
        quote.setAppointment(appointment);
        when(quoteRepository.findWithLinesByAppointmentId(appointment.getId())).thenReturn(Optional.of(quote));
        service.changeStatus(appointment.getId(), AppointmentStatus.CANCELLED);
        verify(quoteStockService).releaseAll(eq(quote), any());
    }

    @Test
    void otherStatusChangesNeverTouchTheStock() {
        Appointment appointment = existing(AppointmentStatus.PLANNED);
        service.changeStatus(appointment.getId(), AppointmentStatus.IN_PROGRESS);
        verify(quoteStockService, never()).releaseAll(any(), any());
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
