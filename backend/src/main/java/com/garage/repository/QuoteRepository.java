package com.garage.repository;

import com.garage.domain.AppointmentStatus;
import com.garage.domain.Quote;
import com.garage.domain.QuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    @EntityGraph(attributePaths = {"lines"})
    Optional<Quote> findWithLinesByAppointmentId(UUID appointmentId);

    /** Lines are not loaded: used to add the quote status and total to lists of appointments. */
    List<Quote> findByAppointmentIdIn(Collection<UUID> appointmentIds);

    /** Sum of the ACCEPTED quotes of the appointments with the given status, dated in [start, end). */
    @Query("""
            select sum(q.total) from Quote q
            where q.status = :quoteStatus
              and q.appointment.status = :appointmentStatus
              and q.appointment.date >= :start and q.appointment.date < :end
            """)
    /** @return null when there is no matching quote */
    BigDecimal sumTotals(@Param("quoteStatus") QuoteStatus quoteStatus,
                         @Param("appointmentStatus") AppointmentStatus appointmentStatus,
                         @Param("start") Instant start, @Param("end") Instant end);

    /**
     * Revenue per month (Europe/Paris, month of the appointment): total of the ACCEPTED quotes of the
     * COMPLETED appointments, same rule as the dashboard. Rows are {month, sum}; months without revenue are absent.
     */
    @Query(value = """
            select cast(extract(month from (a.appointment_date at time zone 'Europe/Paris')) as integer) as m,
                   sum(q.total) as t
            from quotes q
            join appointments a on a.id = q.appointment_id
            where q.status = 'ACCEPTED'
              and a.status = 'COMPLETED'
              and a.appointment_date >= :start and a.appointment_date < :end
            group by 1
            """, nativeQuery = true)
    List<Object[]> revenueByMonth(@Param("start") Instant start, @Param("end") Instant end);
}
