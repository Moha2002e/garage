package com.garage.specification;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Vehicle;
import com.garage.util.PageUtil;
import jakarta.persistence.criteria.Join;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Search criteria on appointments. The service only combines them; the rule "a CLIENT only sees their own
 * appointments" stays in the service, which decides when to add {@link #forClient(UUID)}.
 */
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    /** Appointments of one client. */
    public static Specification<Appointment> forClient(UUID clientId) {
        return (root, query, cb) -> cb.equal(root.get("client").get("id"), clientId);
    }

    /** Appointments of one vehicle. */
    public static Specification<Appointment> forVehicle(UUID vehicleId) {
        return (root, query, cb) -> cb.equal(root.get("vehicle").get("id"), vehicleId);
    }

    /** Free text on reason, client name and vehicle, case insensitive. A blank text matches everything. */
    public static Specification<Appointment> matchesText(String q) {
        if (q == null || q.isBlank()) {
            return Specification.where(null);
        }
        String like = PageUtil.likeTerm(q);
        return (root, query, cb) -> {
            Join<Appointment, Client> client = root.join("client");
            Join<Appointment, Vehicle> vehicle = root.join("vehicle");
            return cb.or(
                    cb.like(cb.lower(root.get("reason")), like, '\\'),
                    cb.like(cb.lower(client.get("lastName")), like, '\\'),
                    cb.like(cb.lower(client.get("firstName")), like, '\\'),
                    cb.like(cb.lower(vehicle.get("brand")), like, '\\'),
                    cb.like(cb.lower(vehicle.get("model")), like, '\\'),
                    cb.like(cb.lower(vehicle.get("plate")), like, '\\'));
        };
    }

    /** One appointment status. A null status matches everything. */
    public static Specification<Appointment> hasStatus(AppointmentStatus status) {
        if (status == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Appointments starting at or after this instant. A null instant matches everything. */
    public static Specification<Appointment> startsFrom(Instant start) {
        if (start == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("date"), start);
    }

    /** Appointments starting strictly before this instant. A null instant matches everything. */
    public static Specification<Appointment> startsBefore(Instant end) {
        if (end == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.lessThan(root.<Instant>get("date"), end);
    }
}
