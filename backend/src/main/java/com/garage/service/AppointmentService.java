package com.garage.service;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Role;
import com.garage.domain.Vehicle;
import com.garage.dto.AppointmentDtos.AppointmentRequest;
import com.garage.dto.AppointmentDtos.AppointmentResponse;
import com.garage.dto.PageResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.NotFoundException;
import com.garage.repository.AppointmentRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import com.garage.util.PageUtil;
import jakarta.persistence.criteria.Join;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

    private final AppointmentRepository appointmentRepository;
    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final CurrentUser currentUser;

    public AppointmentService(AppointmentRepository appointmentRepository, ClientService clientService,
                              VehicleService vehicleService, CurrentUser currentUser) {
        this.appointmentRepository = appointmentRepository;
        this.clientService = clientService;
        this.vehicleService = vehicleService;
        this.currentUser = currentUser;
    }

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
            return PageResponse.from(empty, AppointmentService::toResponse);
        }
        return PageResponse.from(appointmentRepository.findAll(spec, pageable), AppointmentService::toResponse);
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

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        Appointment appointment = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        AuthUser user = currentUser.get();
        if (user.role() == Role.CLIENT
                && (user.clientId() == null || !user.clientId().equals(appointment.getClient().getId()))) {
            // 404 rather than 403: do not reveal that another client's appointment exists.
            throw new NotFoundException("Appointment not found");
        }
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse create(AppointmentRequest request) {
        Client client = clientService.find(request.clientId());
        if (!client.isActive()) {
            throw new BusinessException("Cannot create an appointment for an inactive client");
        }
        Vehicle vehicle = checkedVehicle(request, client);
        requireFuture(request.date());
        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setVehicle(vehicle);
        appointment.setDate(request.date());
        appointment.setReason(request.reason().trim());
        appointment.setStatus(AppointmentStatus.PLANNED);
        return toResponse(appointmentRepository.save(appointment));
    }

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
        requireFuture(request.date());
        appointment.setVehicle(vehicle);
        appointment.setDate(request.date());
        appointment.setReason(request.reason().trim());
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse changeStatus(UUID id, AppointmentStatus target) {
        Appointment appointment = appointmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
        if (!appointment.getStatus().canTransitionTo(target)) {
            throw new BusinessException("Status transition from " + appointment.getStatus() + " to " + target
                    + " is not allowed");
        }
        appointment.setStatus(target);
        return toResponse(appointment);
    }

    private Vehicle checkedVehicle(AppointmentRequest request, Client client) {
        Vehicle vehicle = vehicleService.find(request.vehicleId());
        if (!vehicle.getClient().getId().equals(client.getId())) {
            throw new BusinessException("The vehicle does not belong to this client");
        }
        return vehicle;
    }

    private void requireFuture(Instant date) {
        if (!date.isAfter(Instant.now())) {
            throw new BusinessException("The appointment date must be in the future");
        }
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

    static AppointmentResponse toResponse(Appointment a) {
        Client c = a.getClient();
        Vehicle v = a.getVehicle();
        return new AppointmentResponse(a.getId(), a.getDate(), a.getReason(), a.getStatus(),
                c.getId(), c.getFirstName(), c.getLastName(),
                v.getId(), v.getPlate(), v.getBrand(), v.getModel());
    }
}
