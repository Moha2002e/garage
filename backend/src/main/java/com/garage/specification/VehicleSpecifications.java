package com.garage.specification;

import com.garage.domain.Client;
import com.garage.domain.Vehicle;
import com.garage.util.PageUtil;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

/** Search criteria on vehicles. The service only combines them. */
public final class VehicleSpecifications {

    private VehicleSpecifications() {
    }

    /** Free text on plate, brand, model and owner name, case insensitive. A blank text matches everything. */
    public static Specification<Vehicle> matchesText(String q) {
        if (q == null || q.isBlank()) {
            return Specification.where(null);
        }
        String like = PageUtil.likeTerm(q);
        return (root, query, cb) -> {
            Join<Vehicle, Client> client = root.join("client");
            return cb.or(
                    cb.like(cb.lower(root.get("plate")), like, '\\'),
                    cb.like(cb.lower(root.get("brand")), like, '\\'),
                    cb.like(cb.lower(root.get("model")), like, '\\'),
                    cb.like(cb.lower(client.get("lastName")), like, '\\'),
                    cb.like(cb.lower(client.get("firstName")), like, '\\'));
        };
    }

    /** Active or inactive vehicles. A null status matches everything. */
    public static Specification<Vehicle> hasActive(Boolean active) {
        if (active == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
