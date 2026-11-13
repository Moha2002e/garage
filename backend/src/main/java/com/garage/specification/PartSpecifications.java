package com.garage.specification;

import com.garage.domain.Part;
import com.garage.util.PageUtil;
import org.springframework.data.jpa.domain.Specification;

/** Search criteria on parts. The service only combines them. */
public final class PartSpecifications {

    private PartSpecifications() {
    }

    /** Free text on reference and name, case insensitive. A blank text matches everything. */
    public static Specification<Part> matchesText(String q) {
        if (q == null || q.isBlank()) {
            return Specification.where(null);
        }
        String like = PageUtil.likeTerm(q);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("reference")), like, '\\'),
                cb.like(cb.lower(root.get("name")), like, '\\'));
    }

    /** Active or inactive parts. A null status matches everything. */
    public static Specification<Part> hasActive(Boolean active) {
        if (active == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /** Parts whose stock is at or below their alert threshold. */
    public static Specification<Part> lowStock() {
        return (root, query, cb) -> cb.lessThanOrEqualTo(
                root.<Integer>get("quantityInStock"), root.<Integer>get("lowStockThreshold"));
    }
}
