package com.garage.specification;

import com.garage.domain.Client;
import com.garage.util.PageUtil;
import org.springframework.data.jpa.domain.Specification;

/** Search criteria on clients. The service only combines them. */
public final class ClientSpecifications {

    private ClientSpecifications() {
    }

    /** Free text on last name, first name, e-mail and phone, case insensitive. A blank text matches everything. */
    public static Specification<Client> matchesText(String q) {
        if (q == null || q.isBlank()) {
            return Specification.where(null);
        }
        String like = PageUtil.likeTerm(q);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("lastName")), like, '\\'),
                cb.like(cb.lower(root.get("firstName")), like, '\\'),
                cb.like(cb.lower(root.get("email")), like, '\\'),
                cb.like(cb.coalesce(root.<String>get("phone"), ""), like, '\\'));
    }

    /** Active or inactive clients. A null status matches everything. */
    public static Specification<Client> hasActive(Boolean active) {
        if (active == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
