package com.garage.repository;

import com.garage.domain.Vehicle;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    @Override
    @EntityGraph(attributePaths = {"client"})
    Page<Vehicle> findAll(Specification<Vehicle> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"client"})
    List<Vehicle> findByClientIdAndActiveTrueOrderByPlate(UUID clientId);

    /** Active and inactive vehicles of a client. */
    @EntityGraph(attributePaths = {"client"})
    List<Vehicle> findByClientIdOrderByPlate(UUID clientId);

    @EntityGraph(attributePaths = {"client"})
    java.util.Optional<Vehicle> findWithClientById(UUID id);

    boolean existsByPlate(String plate);

    boolean existsByPlateAndIdNot(String plate, UUID id);

    long countByActive(boolean active);

    /** Active vehicles per brand (case insensitive), most frequent first. Rows are {brand, count}. */
    @Query("""
            select min(v.brand), count(v) from Vehicle v
            where v.active = true
            group by lower(v.brand)
            order by count(v) desc, lower(v.brand) asc
            """)
    List<Object[]> topBrands(Pageable pageable);
}
