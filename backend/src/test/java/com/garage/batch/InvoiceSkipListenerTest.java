package com.garage.batch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.garage.domain.Appointment;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class InvoiceSkipListenerTest {

    @Mock BatchErrorRecorder recorder;

    InvoiceSkipListener listener;

    @BeforeEach
    void setUp() {
        listener = new InvoiceSkipListener(recorder, 42L);
    }

    @Test
    void businessSkipKeepsItsStableCodeAndMessage() {
        UUID id = UUID.randomUUID();
        listener.onSkipInProcess(id, new InvoiceSkipException(InvoiceErrorCode.EMPTY_QUOTE, "Accepted quote has no line"));
        verify(recorder).record(42L, id, InvoiceErrorCode.EMPTY_QUOTE, "Accepted quote has no line");
    }

    @Test
    void uniqueConstraintViolationOnWriteIsDuplicateInvoice() {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        Invoice invoice = new Invoice();
        invoice.setAppointment(appointment);
        Throwable violation = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_invoices_appointment\""));

        listener.onSkipInWrite(invoice, violation);

        verify(recorder).record(42L, appointment.getId(), InvoiceErrorCode.DUPLICATE_INVOICE,
                "Appointment already has an invoice");
    }

    @Test
    void otherIntegrityViolationsAndErrorsAreUnexpectedWithoutLeakingTheirText() {
        UUID first = UUID.randomUUID();
        listener.onSkipInProcess(first, new DataIntegrityViolationException("null value in column \"total\" of mail@x.test"));
        verify(recorder).record(42L, first, InvoiceErrorCode.UNEXPECTED, "Unexpected error: DataIntegrityViolationException");

        UUID second = UUID.randomUUID();
        listener.onSkipInProcess(second, new IllegalStateException("secret@example.com"));
        verify(recorder).record(42L, second, InvoiceErrorCode.UNEXPECTED, "Unexpected error: IllegalStateException");
    }

    @Test
    void readSkipHasNoAppointment() {
        listener.onSkipInRead(new RuntimeException("boom"));
        verify(recorder).record(eq(42L), isNull(), eq(InvoiceErrorCode.UNEXPECTED), anyString());
    }

    @Test
    void aFailureOfTheBookkeepingNeverBreaksTheRun() {
        doThrow(new IllegalStateException("db down")).when(recorder).record(eq(42L), any(), any(), anyString());
        assertDoesNotThrow(() -> listener.onSkipInProcess(UUID.randomUUID(),
                new InvoiceSkipException(InvoiceErrorCode.INVALID_AMOUNT, "x")));
    }
}
