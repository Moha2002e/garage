package com.garage.service;

import com.garage.config.AppProperties;
import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Quote;
import com.garage.domain.Role;
import com.garage.domain.Vehicle;
import com.garage.dto.AppointmentDtos.AppointmentRequest;
import com.garage.dto.AppointmentDtos.AppointmentResponse;
import com.garage.dto.AppointmentDtos.CalendarEvent;
import com.garage.dto.PageResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.QuoteRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import com.garage.util.PageUtil;
import jakarta.persistence.criteria.Join;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Sort SORT = Sort.by(Sort.Direction.DESC, "date");
    private static final Sort HISTORY_SORT = Sort.by(Sort.Direction.DESC, "date").and(Sort.by("id"));
    private static final int MAX_HISTORY_PAGE_SIZE = 50;
    private static final int MIN_DURATION = 15;
    private static final int MAX_DURATION = 480;
    private static final Duration MAX_CALENDAR_RANGE = Duration.ofDays(100);
    private static final int MAX_CALENDAR_EVENTS = 2000;
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;
    /** Used as "exclude nothing" when creating: a native query cannot compare an id with null. */
    private static final UUID NO_APPOINTMENT = new UUID(0, 0);

    private final AppointmentRepository appointmentRepository;
    private final QuoteRepository quoteRepository;
    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final CurrentUser currentUser;
    private final AppProperties props;

    public AppointmentService(AppointmentRepository appointmentRepository, QuoteRepository quoteRepository,
                              ClientService clientService, VehicleService vehicleService,
                              CurrentUser currentUser, AppProperties props) {
        this.appointmentRepository = appointmentRepository;
        this.quoteRepository = quoteRepository;
        this.clientService = clientService;
        this.vehicleService = vehicleService;
        this.currentUser = currentUser;
        this.props = props;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /**
     * A CLIENT only ever sees the appointments of the client record linked to their account;
     * the scoping is enforced here, not only by the endpoint annotations.
     */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(String q, AppointmentStatus status, LocalDate from, LocalDate to,
                                                    int page, int size) {
        Pageable pageable = PageUtil.of(page, size, SORT);
        Specification<Appointment> spec = spec(q, status, from, to);
        if (spec == null) {
            Page<Appointment> empty = new PageImpl<>(List.of(), pageable, 0);
            return PageResponse.from(empty, a -> toResponse(a, null));
        }
        Page<Appointment> result = appointmentRepository.findAll(spec, pageable);
        Map<UUID, Quote> quotes = quotesOf(result.getContent());
        return PageResponse.from(result, a -> toResponse(a, quotes.get(a.getId())));
    }

    /** Same filters as the list, capped, for exports (ADMIN only, enforced by the controller). */
    @Transactional(readOnly = true)
    public List<Appointment> searchForExport(String q, AppointmentStatus status, LocalDate from, LocalDate to,
                                             int maxRows) {
        Specification<Appointment> spec = spec(q, status, from, to);
        if (spec == null) {
            return List.of();
        }
        return appointmentRepository.findAll(spec, PageRequest.of(0, maxRows, SORT)).getContent();
    }

    /**
     * Calendar events overlapping [from, to), without pagination. The window is limited to 100 days.
     * A CLIENT only gets their own appointments (nothing when no client record is linked to the account).
     */
    @Transactional(readOnly = true)
    public List<CalendarEvent> calendar(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new BusinessException("'to' must be after 'from'");
        }
        if (Duration.between(from, to).compareTo(MAX_CALENDAR_RANGE) > 0) {
            throw new BusinessException("The calendar range cannot exceed 100 days");
        }
        // An appointment lasts at most 480 minutes: also look at those that started just before the window.
        Instant lookup = from.minus(Duration.ofMinutes(MAX_DURATION));
        AuthUser user = currentUser.get();
        List<Appointment> found;
        if (user.role() == Role.CLIENT) {
            if (user.clientId() == null) {
                return List.of();
            }
            found = appointmentRepository.findByClientIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                    user.clientId(), lookup, to);
        } else {
            found = appointmentRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(lookup, to);
        }
        Instant now = Instant.now();
        return found.stream()
                .filter(a -> a.getEndDate().isAfter(from))
                .limit(MAX_CALENDAR_EVENTS)
                .map(a -> toCalendarEvent(a, now))
                .toList();
    }

    /** History of a client, newest first (page size capped at 50). A CLIENT only gets their own record (else 404). */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listByClient(UUID clientId, int page, int size) {
        AuthUser user = currentUser.get();
        if (user.role() == Role.CLIENT && !clientId.equals(user.clientId())) {
            throw new NotFoundException("Client not found");
        }
        clientService.find(clientId);
        Specification<Appointment> spec = (root, query, cb) -> cb.equal(root.get("client").get("id"), clientId);
        return historyPage(spec, page, size);
    }

    /** History of a vehicle, newest first (page size capped at 50). A CLIENT only gets their own vehicles (else 404). */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listByVehicle(UUID vehicleId, int page, int size) {
        Vehicle vehicle = vehicleService.findAccessible(vehicleId);
        UUID id = vehicle.getId();
        Specification<Appointment> spec = (root, query, cb) -> cb.equal(root.get("vehicle").get("id"), id);
        return historyPage(spec, page, size);
    }

    private PageResponse<AppointmentResponse> historyPage(Specification<Appointment> spec, int page, int size) {
        Page<Appointment> result = appointmentRepository.findAll(spec,
                PageUtil.ofCapped(page, size, HISTORY_SORT, MAX_HISTORY_PAGE_SIZE));
        Map<UUID, Quote> quotes = quotesOf(result.getContent());
        return PageResponse.from(result, a -> toResponse(a, quotes.get(a.getId())));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        Appointment appointment = findAccessible(id);
        return toResponses(List.of(appointment)).get(0);
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    /** Past dates are accepted on purpose: they let the garage enter its history. */
    @Transactional
    public AppointmentResponse create(AppointmentRequest request) {
        Client client = clientService.find(request.clientId());
        if (!client.isActive()) {
            throw new BusinessException("Cannot create an appointment for an inactive client");
        }
        Vehicle vehicle = checkedVehicle(request, client);
        requireValidDuration(request.durationMinutes());
        checkNoConflict(vehicle.getId(), request.date(), request.durationMinutes(), NO_APPOINTMENT);
        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setVehicle(vehicle);
        appointment.setDate(request.date());
        appointment.setDurationMinutes(request.durationMinutes());
        appointment.setReason(request.reason().trim());
        appointment.setStatus(AppointmentStatus.PLANNED);
        return toResponse(appointmentRepository.save(appointment), null);
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    /** Only a planned appointment can be edited; its client cannot change. */
    @Transactional
    public AppointmentResponse update(UUID id, AppointmentRequest request) {
        Appointment appointment = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        if (appointment.getStatus() != AppointmentStatus.PLANNED) {
            throw new BusinessException("Only a planned appointment can be modified");
        }
        if (!appointment.getClient().getId().equals(request.clientId())) {
            throw new BusinessException("The client of an appointment cannot be changed");
        }
        Vehicle vehicle = checkedVehicle(request, appointment.getClient());
        requireValidDuration(request.durationMinutes());
        // The appointment being edited is excluded, otherwise it would conflict with itself.
        checkNoConflict(vehicle.getId(), request.date(), request.durationMinutes(), appointment.getId());
        appointment.setVehicle(vehicle);
        appointment.setDate(request.date());
        appointment.setDurationMinutes(request.durationMinutes());
        appointment.setReason(request.reason().trim());
        return toResponses(List.of(appointment)).get(0);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : un rendez-vous s'annule (statut CANCELLED), il n'est jamais supprimé.

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    @Transactional
    public AppointmentResponse changeStatus(UUID id, AppointmentStatus target) {
        Appointment appointment = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        if (!appointment.getStatus().canTransitionTo(target)) {
            throw new BusinessException("Status transition from " + appointment.getStatus() + " to " + target
                    + " is not allowed");
        }
        appointment.setStatus(target);
        return toResponses(List.of(appointment)).get(0);
    }

    /**
     * Moves a PLANNED appointment to a new start (drag and drop in the calendar). The duration, client and
     * vehicle do not change. Same schedule rules as an update, the moved appointment excluded from the overlaps.
     */
    @Transactional
    public AppointmentResponse reschedule(UUID id, Instant newDate) {
        Appointment appointment = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        if (appointment.getStatus() != AppointmentStatus.PLANNED) {
            throw new BusinessException("Only a planned appointment can be modified");
        }
        if (newDate == null) {
            throw new BusinessException("The date is required");
        }
        int year = newDate.atZone(PARIS).getYear();
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException("The date must be between the years 2000 and 2100");
        }
        checkNoConflict(appointment.getVehicle().getId(), newDate, appointment.getDurationMinutes(),
                appointment.getId());
        appointment.setDate(newDate);
        return toResponses(List.of(appointment)).get(0);
    }

    /** Loads an appointment the current user may see; another client's appointment is a 404. */
    Appointment findAccessible(UUID id) {
        Appointment appointment = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        AuthUser user = currentUser.get();
        if (user.role() == Role.CLIENT
                && (user.clientId() == null || !user.clientId().equals(appointment.getClient().getId()))) {
            // 404 rather than 403: do not reveal that another client's appointment exists.
            throw new NotFoundException("Appointment not found");
        }
        return appointment;
    }

    /** Responses with quote status and total, using a single query for all the quotes. */
    List<AppointmentResponse> toResponses(List<Appointment> appointments) {
        Map<UUID, Quote> quotes = quotesOf(appointments);
        return appointments.stream().map(a -> toResponse(a, quotes.get(a.getId()))).toList();
    }

    private Map<UUID, Quote> quotesOf(List<Appointment> appointments) {
        Map<UUID, Quote> quotes = new HashMap<>();
        if (appointments.isEmpty()) {
            return quotes;
        }
        List<UUID> ids = appointments.stream().map(Appointment::getId).toList();
        quoteRepository.findByAppointmentIdIn(ids)
                .forEach(q -> quotes.put(q.getAppointment().getId(), q));
        return quotes;
    }

    private Vehicle checkedVehicle(AppointmentRequest request, Client client) {
        Vehicle vehicle = vehicleService.find(request.vehicleId());
        if (!vehicle.getClient().getId().equals(client.getId())) {
            throw new BusinessException("The vehicle does not belong to this client");
        }
        if (!vehicle.isActive()) {
            throw new BusinessException("The vehicle is inactive");
        }
        return vehicle;
    }

    private void requireValidDuration(Integer minutes) {
        if (minutes == null || minutes < MIN_DURATION || minutes > MAX_DURATION || minutes % MIN_DURATION != 0) {
            throw new BusinessException("Duration must be between 15 and 480 minutes, in steps of 15");
        }
    }

    /**
     * Schedule rules on PLANNED and IN_PROGRESS appointments: the same vehicle cannot be booked twice
     * at the same time, and the number of simultaneous appointments cannot exceed the garage capacity.
     * The check is not locked: two simultaneous requests could still both pass.
     */
    private void checkNoConflict(UUID vehicleId, Instant start, int durationMinutes, UUID excludedId) {
        Instant end = start.plus(Duration.ofMinutes(durationMinutes));
        if (appointmentRepository.existsOverlappingForVehicle(vehicleId, start, end, excludedId)) {
            throw new ConflictException("This vehicle already has an appointment during this time slot");
        }
        List<Appointment> overlapping = appointmentRepository.findOverlapping(start, end, excludedId);
        if (peakConcurrency(start, end, overlapping) >= props.garage().capacity()) {
            throw new ConflictException("The garage has reached its capacity during this time slot");
        }
    }

    /**
     * Highest number of the given appointments that run at the same moment inside [start, end).
     * The count only changes when an appointment starts, so it is enough to look at the start of the
     * interval and at the start of each appointment that begins inside it.
     */
    static int peakConcurrency(Instant start, Instant end, List<Appointment> others) {
        int peak = 0;
        List<Instant> moments = new java.util.ArrayList<>();
        moments.add(start);
        for (Appointment o : others) {
            if (!o.getDate().isBefore(start) && o.getDate().isBefore(end)) {
                moments.add(o.getDate());
            }
        }
        for (Instant moment : moments) {
            int running = 0;
            for (Appointment o : others) {
                if (!o.getDate().isAfter(moment) && o.getEndDate().isAfter(moment)) {
                    running++;
                }
            }
            peak = Math.max(peak, running);
        }
        return peak;
    }

    /** @return null when the caller is a CLIENT without a linked client record (nothing visible) */
    private Specification<Appointment> spec(String q, AppointmentStatus status, LocalDate from, LocalDate to) {
        Specification<Appointment> spec = Specification.where(null);
        AuthUser user = currentUser.get();
        if (user.role() == Role.CLIENT) {
            if (user.clientId() == null) {
                return null;
            }
            UUID clientId = user.clientId();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("client").get("id"), clientId));
        }
        if (q != null && !q.isBlank()) {
            String like = PageUtil.likeTerm(q);
            spec = spec.and((root, query, cb) -> {
                Join<Appointment, Client> client = root.join("client");
                Join<Appointment, Vehicle> vehicle = root.join("vehicle");
                return cb.or(
                        cb.like(cb.lower(root.get("reason")), like, '\\'),
                        cb.like(cb.lower(client.get("lastName")), like, '\\'),
                        cb.like(cb.lower(client.get("firstName")), like, '\\'),
                        cb.like(cb.lower(vehicle.get("brand")), like, '\\'),
                        cb.like(cb.lower(vehicle.get("model")), like, '\\'),
                        cb.like(cb.lower(vehicle.get("plate")), like, '\\'));
            });
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            Instant start = from.atStartOfDay(PARIS).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("date"), start));
        }
        if (to != null) {
            Instant end = to.plusDays(1).atStartOfDay(PARIS).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("date"), end));
        }
        return spec;
    }

    /** 0 to 100: elapsed share of the appointment while IN_PROGRESS, 100 when COMPLETED, 0 otherwise. */
    static int progressPercent(Appointment a, Instant now) {
        if (a.getStatus() == AppointmentStatus.COMPLETED) {
            return 100;
        }
        if (a.getStatus() != AppointmentStatus.IN_PROGRESS || a.getDurationMinutes() <= 0) {
            return 0;
        }
        long total = Duration.between(a.getDate(), a.getEndDate()).toMillis();
        long elapsed = Duration.between(a.getDate(), now).toMillis();
        return (int) Math.max(0, Math.min(100, Math.round(elapsed * 100.0 / total)));
    }

    static CalendarEvent toCalendarEvent(Appointment a, Instant now) {
        Client c = a.getClient();
        Vehicle v = a.getVehicle();
        return new CalendarEvent(a.getId(), a.getDate(), a.getEndDate(), a.getReason(), a.getStatus(),
                (c.getFirstName() + " " + c.getLastName()).trim(),
                (v.getBrand() + " " + v.getModel()).trim(),
                a.getDurationMinutes(), progressPercent(a, now));
    }

    static AppointmentResponse toResponse(Appointment a, Quote quote) {
        Client c = a.getClient();
        Vehicle v = a.getVehicle();
        return new AppointmentResponse(a.getId(), a.getDate(), a.getReason(), a.getStatus(),
                c.getId(), c.getFirstName(), c.getLastName(),
                v.getId(), v.getPlate(), v.getBrand(), v.getModel(),
                a.getDurationMinutes(),
                quote == null ? null : quote.getStatus(),
                quote == null ? null : quote.getTotal());
    }
}
