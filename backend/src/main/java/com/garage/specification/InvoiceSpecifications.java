package com.garage.specification;

import com.garage.domain.Appointment;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Vehicle;
import com.garage.util.PageUtil;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

/** Search criteria on invoices. The service only combines them. */
public final class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    /** Free text on number, client name and vehicle, case insensitive. A blank text matches everything. */
    public static Specification<Invoice> matchesText(String q) {
        if (q == null || q.isBlank()) {
            return Specification.where(null);
        }
        String like = PageUtil.likeTerm(q);
        return (root, query, cb) -> {
            Join<Invoice, Client> client = root.join("client");
            Join<Invoice, Appointment> appointment = root.join("appointment");
            Join<Appointment, Vehicle> vehicle = appointment.join("vehicle");
            return cb.or(
                    cb.like(cb.lower(root.get("number")), like, '\\'),
                    cb.like(cb.lower(client.get("lastName")), like, '\\'),
                    cb.like(cb.lower(client.get("firstName")), like, '\\'),
                    cb.like(cb.lower(vehicle.get("brand")), like, '\\'),
                    cb.like(cb.lower(vehicle.get("model")), like, '\\'),
                    cb.like(cb.lower(vehicle.get("plate")), like, '\\'));
        };
    }

    /** One invoice status. A null status matches everything. */
    public static Specification<Invoice> hasStatus(InvoiceStatus status) {
        if (status == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
