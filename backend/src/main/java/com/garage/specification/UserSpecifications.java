package com.garage.specification;

import com.garage.domain.Role;
import com.garage.domain.User;
import com.garage.util.PageUtil;
import org.springframework.data.jpa.domain.Specification;

/** Search criteria on user accounts. The service only combines them. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Free text on e-mail, last name and first name, case insensitive. A blank text matches everything. */
    public static Specification<User> matchesText(String q) {
        if (q == null || q.isBlank()) {
            return Specification.where(null);
        }
        String like = PageUtil.likeTerm(q);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), like, '\\'),
                cb.like(cb.lower(root.get("lastName")), like, '\\'),
                cb.like(cb.lower(root.get("firstName")), like, '\\'));
    }

    /** One role. A null role matches everything. */
    public static Specification<User> hasRole(Role role) {
        if (role == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }
}
