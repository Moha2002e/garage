package com.garage.batch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.domain.InvoiceErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

class InvoiceSkipPolicyTest {

    private final InvoiceSkipPolicy policy = new InvoiceSkipPolicy(3);

    @Test
    void anErrorOnOneAppointmentIsSkipped() {
        assertTrue(policy.shouldSkip(new InvoiceSkipException(InvoiceErrorCode.EMPTY_QUOTE, "x"), 0));
        assertTrue(policy.shouldSkip(new DataIntegrityViolationException("duplicate"), 1));
        assertTrue(policy.shouldSkip(new IllegalStateException("boom"), 2));
        // The framework asks with -1 while it replays a failed chunk item by item.
        assertTrue(policy.shouldSkip(new IllegalStateException("boom"), -1));
    }

    @Test
    void infrastructureFailuresAndJvmErrorsFailTheRun() {
        assertFalse(policy.shouldSkip(new DataAccessResourceFailureException("db down"), 0));
        assertFalse(policy.shouldSkip(new CannotCreateTransactionException("no connection"), 0));
        assertFalse(policy.shouldSkip(new OutOfMemoryError(), 0));
    }

    @Test
    void tooManySkipsFailTheRun() {
        assertThrows(SkipLimitExceededException.class,
                () -> policy.shouldSkip(new IllegalStateException("boom"), 3));
    }
}
