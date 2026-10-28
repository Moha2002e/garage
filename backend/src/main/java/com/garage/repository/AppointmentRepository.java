package com.garage.repository;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import java.time.Instant;
import java.util.Collection;
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

    /** Appointments with client and vehicle for a set of ids (labels of the batch errors). */
    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Appointment> findWithDetailsByIdIn(Collection<UUID> ids);

    /** Appointments with the given status, earliest first, at most 20 (dashboard, IN_PROGRESS). */
    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Appointment> findTop20ByStatusOrderByDateAsc(AppointmentStatus status);

    /** The next PLANNED appointments from {@code from}, earliest first, at most 5 (dashboard). */
    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Appointment> findTop5ByStatusAndDateGreaterThanEqualOrderByDateAsc(AppointmentStatus status, Instant from);

    boolean existsByVehicleIdAndStatusIn(UUID vehicleId, Collection<AppointmentStatus> statuses);

    long countByStatus(AppointmentStatus status);

    long countByStatusAndDateGreaterThanEqualAndDateLessThan(AppointmentStatus status, Instant start, Instant end);

    /** Appointments of a time window with the given statuses, earliest first (dashboard). */
    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Appointment> findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
            Collection<AppointmentStatus> statuses, Instant start, Instant end);

    /**
     * Appointments PLANNED or IN_PROGRESS whose interval [date, date + duration) overlaps [start, end),
     * ignoring the appointment {@code excludeId} (pass a zero UUID when creating).
     */
    @Query(value = """
            select a.* from appointments a
            where a.status in ('PLANNED', 'IN_PROGRESS')
              and a.id <> :excludeId
              and a.appointment_date < :end
              and a.appointment_date + make_interval(mins => a.duration_minutes) > :start
            """, nativeQuery = true)
    List<Appointment> findOverlapping(@Param("start") Instant start, @Param("end") Instant end,
                                      @Param("excludeId") UUID excludeId);

    /** Same overlap rule, restricted to one vehicle: true when the vehicle is already booked. */
    @Query(value = """
            select count(*) > 0 from appointments a
            where a.vehicle_id = :vehicleId
              and a.status in ('PLANNED', 'IN_PROGRESS')
              and a.id <> :excludeId
              and a.appointment_date < :end
              and a.appointment_date + make_interval(mins => a.duration_minutes) > :start
            """, nativeQuery = true)
    boolean existsOverlappingForVehicle(@Param("vehicleId") UUID vehicleId, @Param("start") Instant start,
                                        @Param("end") Instant end, @Param("excludeId") UUID excludeId);

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

    /** Calendar window (no pagination): appointments whose start is in [start, end), earliest first. */
    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Appointment> findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(Instant start, Instant end);

    /** Same window restricted to one client (a CLIENT only sees their own appointments). */
    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Appointment> findByClientIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
            UUID clientId, Instant start, Instant end);
}
