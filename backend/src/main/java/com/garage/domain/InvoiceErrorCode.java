package com.garage.domain;

/** Stable codes written in batch_errors when the invoice job ignores an appointment. Never rename them. */
public enum InvoiceErrorCode {
    /** The appointment has no ACCEPTED quote (defensive: the reader only selects appointments that have one). */
    NO_ACCEPTED_QUOTE,
    /** The accepted quote has no active line. */
    EMPTY_QUOTE,
    /** A quantity, a unit price or a total is not usable (negative, zero total, inconsistent or too large). */
    INVALID_AMOUNT,
    /** The appointment already has an invoice (checked by the processor and by the unique constraint). */
    DUPLICATE_INVOICE,
    /** Any other error. */
    UNEXPECTED
}
