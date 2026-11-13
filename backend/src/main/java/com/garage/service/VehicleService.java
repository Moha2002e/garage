package com.garage.service;

import com.garage.domain.AppointmentStatus;
import com.garage.domain.Role;
import com.garage.domain.Vehicle;
import com.garage.dto.PageResponse;
import com.garage.dto.VehicleDtos.VehicleRequest;
import com.garage.dto.VehicleDtos.VehicleResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.mapper.VehicleMapper;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.VehicleRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import com.garage.specification.VehicleSpecifications;
import com.garage.util.PageUtil;
import com.garage.util.PlateUtil;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private static final Sort SORT = Sort.by("brand", "model", "plate");

    private final VehicleRepository vehicleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientService clientService;
    private final CurrentUser currentUser;
    private final VehicleMapper mapper;

    public VehicleService(VehicleRepository vehicleRepository, AppointmentRepository appointmentRepository,
                          ClientService clientService, CurrentUser currentUser, VehicleMapper mapper) {
        this.vehicleRepository = vehicleRepository;
        this.appointmentRepository = appointmentRepository;
        this.clientService = clientService;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /** Paginated list for the vehicles page: sorted by brand then model, active filter optional. */
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> search(String q, Boolean active, int page, int size) {
        return PageResponse.from(vehicleRepository.findAll(spec(q, active), PageUtil.of(page, size, SORT)),
                mapper::toResponse);
    }

    /** Only active vehicles: this list feeds the vehicle choice of an appointment. */
    @Transactional(readOnly = true)
    public List<VehicleResponse> listByClient(UUID clientId) {
        return listByClient(clientId, true);
    }

    /** active = true: active vehicles only; null (or false): every vehicle of the client, active or not. */
    @Transactional(readOnly = true)
    public List<VehicleResponse> listByClient(UUID clientId, Boolean active) {
        clientService.find(clientId);
        List<Vehicle> vehicles = Boolean.TRUE.equals(active)
                ? vehicleRepository.findByClientIdAndActiveTrueOrderByPlate(clientId)
                : vehicleRepository.findByClientIdOrderByPlate(clientId);
        return vehicles.stream().map(mapper::toResponse).toList();
    }

    /** One vehicle with its client; a CLIENT only gets a vehicle of their own client record (else 404). */
    @Transactional(readOnly = true)
    public VehicleResponse get(UUID id) {
        return mapper.toResponse(findAccessible(id));
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        String plate = normalizedPlate(request.plate());
        if (vehicleRepository.existsByPlate(plate)) {
            throw new ConflictException("A vehicle with this plate already exists");
        }
        Vehicle vehicle = mapper.toEntity(request, plate, clientService.find(request.clientId()));
        return mapper.toResponse(vehicleRepository.save(vehicle));
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = find(id);
        String plate = normalizedPlate(request.plate());
        if (vehicleRepository.existsByPlateAndIdNot(plate, id)) {
            throw new ConflictException("A vehicle with this plate already exists");
        }
        if (!vehicle.getClient().getId().equals(request.clientId())) {
            throw new BusinessException("A vehicle cannot be moved to another client");
        }
        mapper.update(request, plate, vehicle);
        return mapper.toResponse(vehicle);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    /** Deactivation, never a physical deletion. Refused while the vehicle has upcoming work. */
    @Transactional
    public void deactivate(UUID id) {
        Vehicle vehicle = find(id);
        if (appointmentRepository.existsByVehicleIdAndStatusIn(id,
                List.of(AppointmentStatus.PLANNED, AppointmentStatus.IN_PROGRESS))) {
            throw new ConflictException("This vehicle has planned or in progress appointments and cannot be deactivated");
        }
        vehicle.setActive(false);
    }

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    @Transactional
    public VehicleResponse reactivate(UUID id) {
        Vehicle vehicle = find(id);
        vehicle.setActive(true);
        return mapper.toResponse(vehicle);
    }

    /** Also used by the appointment service; the client is loaded with the vehicle. */
    Vehicle find(UUID id) {
        return vehicleRepository.findWithClientById(id).orElseThrow(() -> new NotFoundException("Vehicle not found"));
    }

    /** Loads a vehicle the current user may see; another client's vehicle is a 404, not a 403. */
    Vehicle findAccessible(UUID id) {
        Vehicle vehicle = find(id);
        AuthUser user = currentUser.get();
        if (user.role() == Role.CLIENT
                && (user.clientId() == null || !user.clientId().equals(vehicle.getClient().getId()))) {
            throw new NotFoundException("Vehicle not found");
        }
        return vehicle;
    }

    private String normalizedPlate(String raw) {
        String plate = PlateUtil.normalize(raw);
        if (plate == null) {
            // The submitted value is not echoed: a plate is personal data.
            throw new BusinessException("Plate must follow the French format AA-123-AA");
        }
        return plate;
    }

    private Specification<Vehicle> spec(String q, Boolean active) {
        return Specification.where(VehicleSpecifications.matchesText(q))
                .and(VehicleSpecifications.hasActive(active));
    }
}
