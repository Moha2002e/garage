package com.garage.batch;

import com.garage.domain.BatchError;
import com.garage.domain.InvoiceErrorCode;
import com.garage.repository.BatchErrorRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes batch_errors in its own transaction: the row survives even when the chunk transaction that raised the
 * error is rolled back by Spring Batch.
 */
@Component
public class BatchErrorRecorder {

    static final int MAX_MESSAGE = 500;

    private final BatchErrorRepository repository;

    public BatchErrorRecorder(BatchErrorRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long runId, UUID appointmentId, InvoiceErrorCode code, String message) {
        BatchError error = new BatchError();
        error.setRunId(runId);
        error.setAppointmentId(appointmentId);
        error.setCode(code);
        error.setMessage(truncate(message));
        error.setOccurredAt(Instant.now());
        repository.saveAndFlush(error);
    }

    static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "No detail";
        }
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE);
    }
}
