package com.garage.batch;

import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * An error on one appointment never blocks the others: every exception is skipped, except
 * <ul>
 *   <li>JVM errors and infrastructure failures (database unreachable): the whole run must fail, otherwise every
 *   remaining appointment would be reported as an error;</li>
 *   <li>more than {@code limit} skipped items in one run: the run fails (something systemic is wrong).</li>
 * </ul>
 */
public class InvoiceSkipPolicy implements SkipPolicy {

    private final int limit;

    public InvoiceSkipPolicy(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        if (t instanceof Error || t instanceof DataAccessResourceFailureException
                || t instanceof CannotCreateTransactionException) {
            return false;
        }
        if (skipCount >= limit) {
            throw new SkipLimitExceededException(limit, t);
        }
        return true;
    }
}
