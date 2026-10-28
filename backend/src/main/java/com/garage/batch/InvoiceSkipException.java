package com.garage.batch;

import com.garage.domain.InvoiceErrorCode;

/**
 * Thrown by the processor when one appointment cannot be invoiced. The step skips the appointment and the
 * skip listener writes the code in batch_errors. The message must never contain personal data.
 */
public class InvoiceSkipException extends RuntimeException {

    private final InvoiceErrorCode code;

    public InvoiceSkipException(InvoiceErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public InvoiceErrorCode getCode() {
        return code;
    }
}
