package com.garage.repository;

import com.garage.domain.AppointmentStatus;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.QuoteStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    @Override
    @EntityGraph(attributePaths = {"client", "appointment", "appointment.vehicle"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    /** With the lines, for the detail and the payment. */
    @EntityGraph(attributePaths = {"client", "appointment", "appointment.vehicle", "lines"})
    Optional<Invoice> findWithDetailsById(UUID id);

    boolean existsByAppointmentId(UUID appointmentId);

    /** The invoice of an appointment with its lines (at most one: the appointment id is unique). */
    @EntityGraph(attributePaths = {"lines"})
    Optional<Invoice> findWithLinesByAppointmentId(UUID appointmentId);

    long countByStatus(InvoiceStatus status);

    /** @return null when there is no invoice with this status */
    @Query("select sum(i.total) from Invoice i where i.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") InvoiceStatus status);

    /**
     * Appointments to invoice: COMPLETED, with an ACCEPTED quote, and without invoice. Oldest first.
     * Only ids are returned: the job reads a snapshot of ids, so invoices written during the run cannot shift a page.
     */
    @Query("""
            select a.id from Appointment a
            where a.status = :appointmentStatus
              and exists (select 1 from Quote q where q.appointment.id = a.id and q.status = :quoteStatus)
              and not exists (select 1 from Invoice i where i.appointment.id = a.id)
            order by a.date asc, a.id asc
            """)
    List<UUID> findInvoiceableIds(@Param("appointmentStatus") AppointmentStatus appointmentStatus,
                                  @Param("quoteStatus") QuoteStatus quoteStatus);

    @Query("""
            select count(a) from Appointment a
            where a.status = :appointmentStatus
              and exists (select 1 from Quote q where q.appointment.id = a.id and q.status = :quoteStatus)
              and not exists (select 1 from Invoice i where i.appointment.id = a.id)
            """)
    long countInvoiceable(@Param("appointmentStatus") AppointmentStatus appointmentStatus,
                          @Param("quoteStatus") QuoteStatus quoteStatus);

    default List<UUID> findInvoiceableIds() {
        return findInvoiceableIds(AppointmentStatus.COMPLETED, QuoteStatus.ACCEPTED);
    }

    default long countInvoiceable() {
        return countInvoiceable(AppointmentStatus.COMPLETED, QuoteStatus.ACCEPTED);
    }
}
