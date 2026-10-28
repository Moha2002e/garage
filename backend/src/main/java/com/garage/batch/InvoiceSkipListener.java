package com.garage.batch;

import com.garage.domain.Invoice;
import com.garage.domain.InvoiceErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Turns every skipped item into a row of batch_errors with a stable code. Step scoped: {@code runId} is the
 * id of the job execution of the current run. Messages carry no personal data (no exception text from the database).
 */
public class InvoiceSkipListener implements SkipListener<UUID, Invoice> {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSkipListener.class);
    static final String DUPLICATE_CONSTRAINT = "uq_invoices_appointment";

    private final BatchErrorRecorder recorder;
    private final long runId;

    public InvoiceSkipListener(BatchErrorRecorder recorder, long runId) {
        this.recorder = recorder;
        this.runId = runId;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        record(null, t);
    }

    @Override
    public void onSkipInProcess(UUID appointmentId, Throwable t) {
        record(appointmentId, t);
    }

    @Override
    public void onSkipInWrite(Invoice invoice, Throwable t) {
        UUID appointmentId = invoice.getAppointment() == null ? null : invoice.getAppointment().getId();
        record(appointmentId, t);
    }

    private void record(UUID appointmentId, Throwable t) {
        try {
            recorder.record(runId, appointmentId, codeOf(t), messageOf(t));
        } catch (RuntimeException e) {
            // Never let the bookkeeping of an error break the run.
            log.error("Could not record a batch error: {}", e.getClass().getSimpleName());
        }
    }

    static InvoiceErrorCode codeOf(Throwable t) {
        if (t instanceof InvoiceSkipException skip) {
            return skip.getCode();
        }
        if (t instanceof DataIntegrityViolationException && mentionsDuplicateConstraint(t)) {
            return InvoiceErrorCode.DUPLICATE_INVOICE;
        }
        return InvoiceErrorCode.UNEXPECTED;
    }

    static String messageOf(Throwable t) {
        if (t instanceof InvoiceSkipException skip) {
            return skip.getMessage();
        }
        if (codeOf(t) == InvoiceErrorCode.DUPLICATE_INVOICE) {
            return "Appointment already has an invoice";
        }
        return "Unexpected error: " + t.getClass().getSimpleName();
    }

    private static boolean mentionsDuplicateConstraint(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            String text = cause.getMessage();
            if (text != null && text.contains(DUPLICATE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
