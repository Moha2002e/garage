package com.garage.repository;

import com.garage.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /** Journal of one part, newest first. */
    Page<StockMovement> findByPartIdOrderByCreatedAtDesc(UUID partId, Pageable pageable);

    /** Guard against applying the same business operation twice (for example one appointment closed twice). */
    boolean existsByPartIdAndReason(UUID partId, String reason);
}
