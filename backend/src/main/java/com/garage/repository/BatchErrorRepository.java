package com.garage.repository;

import com.garage.domain.BatchError;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchErrorRepository extends JpaRepository<BatchError, UUID> {

    Page<BatchError> findByRunId(Long runId, Pageable pageable);

    long countByRunId(Long runId);
}
