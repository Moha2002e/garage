package com.garage.repository;

import com.garage.domain.Vehicle;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByClientIdOrderByPlate(UUID clientId);

    boolean existsByPlate(String plate);

    boolean existsByPlateAndIdNot(String plate, UUID id);
}
