package com.garage.repository;

import com.garage.domain.Part;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PartRepository extends JpaRepository<Part, UUID>, JpaSpecificationExecutor<Part> {

    boolean existsByReference(String reference);

    boolean existsByReferenceAndIdNot(String reference, UUID id);

    long countByActive(boolean active);

    /** Active parts whose stock is at or below their alert threshold. */
    @Query("select count(p) from Part p where p.active = true and p.quantityInStock <= p.lowStockThreshold")
    long countLowStock();
}
