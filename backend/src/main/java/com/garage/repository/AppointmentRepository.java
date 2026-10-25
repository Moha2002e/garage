package com.garage.repository;

import com.garage.domain.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    @Override
    @EntityGraph(attributePaths = {"client", "vehicle"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Appointment> findAll(Specification<Appointment> spec);

    @EntityGraph(attributePaths = {"client", "vehicle"})
    java.util.Optional<Appointment> findWithDetailsById(UUID id);

    boolean existsByVehicleId(UUID vehicleId);

    /**
     * Counts appointments per month (Europe/Paris) and status. Rows are {month, status, count}.
     */
    @Query(value = """
            select cast(extract(month from (a.appointment_date at time zone 'Europe/Paris')) as integer) as m,
                   a.status as s,
                   count(*) as c
            from appointments a
            where a.appointment_date >= :start and a.appointment_date < :end
            group by 1, 2
            """, nativeQuery = true)
    List<Object[]> countByMonthAndStatus(@Param("start") Instant start, @Param("end") Instant end);
}
