package com.garage.service;

import com.garage.domain.Vehicle;
import com.garage.dto.VehicleDtos.VehicleRequest;
import com.garage.dto.VehicleDtos.VehicleResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.VehicleRepository;
import com.garage.util.PlateUtil;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientService clientService;

    public VehicleService(VehicleRepository vehicleRepository, AppointmentRepository appointmentRepository,
                          ClientService clientService) {
        this.vehicleRepository = vehicleRepository;
        this.appointmentRepository = appointmentRepository;
        this.clientService = clientService;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listByClient(UUID clientId) {
        clientService.find(clientId);
        return vehicleRepository.findByClientIdOrderByPlate(clientId).stream().map(VehicleService::toResponse).toList();
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        String plate = normalizedPlate(request.plate());
        if (vehicleRepository.existsByPlate(plate)) {
            throw new ConflictException("A vehicle with this plate already exists");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setClient(clientService.find(request.clientId()));
        apply(vehicle, request, plate);
        return toResponse(vehicleRepository.save(vehicle));
    }

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
        apply(vehicle, request, plate);
        return toResponse(vehicle);
    }

    @Transactional
    public void delete(UUID id) {
        Vehicle vehicle = find(id);
        if (appointmentRepository.existsByVehicleId(id)) {
            throw new ConflictException("This vehicle has appointments and cannot be deleted");
        }
        vehicleRepository.delete(vehicle);
    }

    Vehicle find(UUID id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new NotFoundException("Vehicle not found"));
    }

    private String normalizedPlate(String raw) {
        String plate = PlateUtil.normalize(raw);
        if (plate == null) {
            // The submitted value is not echoed: a plate is personal data.
            throw new BusinessException("Plate must follow the French format AA-123-AA");
        }
        return plate;
    }

    private void apply(Vehicle vehicle, VehicleRequest request, String plate) {
        vehicle.setPlate(plate);
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
    }

    static VehicleResponse toResponse(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getClient().getId(), v.getPlate(), v.getBrand(), v.getModel());
    }
}
