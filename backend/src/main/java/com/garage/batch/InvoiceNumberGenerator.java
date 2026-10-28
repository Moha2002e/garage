package com.garage.batch;

import com.garage.service.AppointmentService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Next invoice number FAC-AAAA-000001 from the PostgreSQL sequence invoice_number_seq. A sequence is not
 * transactional: a number taken by an invoice that is then not written is never reused (gaps are possible).
 */
@Component
public class InvoiceNumberGenerator {

    private final EntityManager entityManager;

    public InvoiceNumberGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public String next(Instant issuedAt) {
        Number value = (Number) entityManager.createNativeQuery("select nextval('invoice_number_seq')")
                .getSingleResult();
        return format(issuedAt.atZone(AppointmentService.PARIS).getYear(), value.longValue());
    }

    static String format(int year, long sequence) {
        return String.format("FAC-%04d-%06d", year, sequence);
    }
}
