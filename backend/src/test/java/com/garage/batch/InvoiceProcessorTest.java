package com.garage.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceErrorCode;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.domain.QuoteStatus;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.InvoiceRepository;
import com.garage.repository.QuoteRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceProcessorTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock QuoteRepository quoteRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock InvoiceNumberGenerator numberGenerator;

    InvoiceProcessor processor;
    Appointment appointment;
    Client client;

    @BeforeEach
    void setUp() {
        processor = new InvoiceProcessor(appointmentRepository, quoteRepository, invoiceRepository, numberGenerator);
        client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setClient(client);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findWithDetailsById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(invoiceRepository.existsByAppointmentId(appointment.getId())).thenReturn(false);
        when(numberGenerator.next(any())).thenReturn("FAC-2026-000001");
    }

    private static QuoteLine line(int order, String description, String quantity, String unitPrice, boolean active) {
        QuoteLine line = new QuoteLine();
        line.setSortOrder(order);
        line.setDescription(description);
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(unitPrice));
        line.setActive(active);
        return line;
    }

    private Quote quote(QuoteStatus status, String total, QuoteLine... lines) {
        Quote quote = new Quote();
        quote.setId(UUID.randomUUID());
        quote.setAppointment(appointment);
        quote.setStatus(status);
        quote.setTotal(new BigDecimal(total));
        for (QuoteLine line : lines) {
            line.setQuote(quote);
            quote.getLines().add(line);
        }
        when(quoteRepository.findWithLinesByAppointmentId(appointment.getId())).thenReturn(Optional.of(quote));
        return quote;
    }

    private InvoiceErrorCode codeOfFailure() {
        InvoiceSkipException e = assertThrows(InvoiceSkipException.class, () -> processor.process(appointment.getId()));
        return e.getCode();
    }

    @Test
    void buildsTheInvoiceWithNumberTotalAndCopiedLines() {
        quote(QuoteStatus.ACCEPTED, "181.00",
                line(0, "Brake pads", "2", "45.50", true),
                line(1, "Labour", "1.5", "60", true));

        Invoice invoice = processor.process(appointment.getId());

        assertEquals("FAC-2026-000001", invoice.getNumber());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        assertEquals(new BigDecimal("181.00"), invoice.getTotal());
        assertSame(appointment, invoice.getAppointment());
        assertSame(client, invoice.getClient());
        assertNotNull(invoice.getIssuedAt());
        assertNull(invoice.getPaidAt());
        assertEquals(2, invoice.getLines().size());
        var first = invoice.getLines().get(0);
        assertEquals("Brake pads", first.getDescription());
        assertEquals(new BigDecimal("2"), first.getQuantity());
        assertEquals(new BigDecimal("45.50"), first.getUnitPrice());
        assertEquals(new BigDecimal("91.00"), first.getTotal());
        assertEquals(0, first.getSortOrder());
        assertSame(invoice, first.getInvoice());
        assertEquals(new BigDecimal("90.00"), invoice.getLines().get(1).getTotal());
        assertEquals(1, invoice.getLines().get(1).getSortOrder());
    }

    @Test
    void replacedQuoteLinesAreNotCopied() {
        quote(QuoteStatus.ACCEPTED, "10.00",
                line(0, "Old line", "1", "99", false),
                line(1, "Oil", "1", "10", true));

        Invoice invoice = processor.process(appointment.getId());

        assertEquals(1, invoice.getLines().size());
        assertEquals("Oil", invoice.getLines().get(0).getDescription());
        assertEquals(new BigDecimal("10.00"), invoice.getTotal());
    }

    @Test
    void lineTotalsAreRoundedHalfUpLikeTheQuote() {
        // 0.25 x 10.10 = 2.525, rounded to 2.53
        quote(QuoteStatus.ACCEPTED, "2.53", line(0, "Oil", "0.25", "10.10", true));
        assertEquals(new BigDecimal("2.53"), processor.process(appointment.getId()).getTotal());
    }

    @Test
    void quoteWithoutActiveLineIsEmptyQuoteAndUsesNoNumber() {
        quote(QuoteStatus.ACCEPTED, "0.00");
        assertEquals(InvoiceErrorCode.EMPTY_QUOTE, codeOfFailure());
        quote(QuoteStatus.ACCEPTED, "0.00", line(0, "Replaced", "1", "5", false));
        assertEquals(InvoiceErrorCode.EMPTY_QUOTE, codeOfFailure());
        verify(numberGenerator, never()).next(any());
    }

    @Test
    void unusableAmountsAreInvalidAmountAndUseNoNumber() {
        quote(QuoteStatus.ACCEPTED, "0.00", line(0, "Zero quantity", "0", "10", true));
        assertEquals(InvoiceErrorCode.INVALID_AMOUNT, codeOfFailure());
        quote(QuoteStatus.ACCEPTED, "-5.00", line(0, "Negative price", "1", "-5", true));
        assertEquals(InvoiceErrorCode.INVALID_AMOUNT, codeOfFailure());
        quote(QuoteStatus.ACCEPTED, "0.00", line(0, "Free", "1", "0", true));
        assertEquals(InvoiceErrorCode.INVALID_AMOUNT, codeOfFailure());
        quote(QuoteStatus.ACCEPTED, "99.00", line(0, "Total differs", "1", "10", true));
        assertEquals(InvoiceErrorCode.INVALID_AMOUNT, codeOfFailure());
        quote(QuoteStatus.ACCEPTED, "9999999999.99", line(0, "Too large", "1", "10000000000.00", true));
        assertEquals(InvoiceErrorCode.INVALID_AMOUNT, codeOfFailure());
        verify(numberGenerator, never()).next(any());
    }

    @Test
    void quoteThatIsNotAcceptedOrMissingIsNoAcceptedQuote() {
        quote(QuoteStatus.DRAFT, "10.00", line(0, "Oil", "1", "10", true));
        assertEquals(InvoiceErrorCode.NO_ACCEPTED_QUOTE, codeOfFailure());
        quote(QuoteStatus.REFUSED, "10.00", line(0, "Oil", "1", "10", true));
        assertEquals(InvoiceErrorCode.NO_ACCEPTED_QUOTE, codeOfFailure());
        when(quoteRepository.findWithLinesByAppointmentId(appointment.getId())).thenReturn(Optional.empty());
        assertEquals(InvoiceErrorCode.NO_ACCEPTED_QUOTE, codeOfFailure());
    }

    @Test
    void anAppointmentThatAlreadyHasAnInvoiceIsNeverInvoicedAgain() {
        quote(QuoteStatus.ACCEPTED, "10.00", line(0, "Oil", "1", "10", true));
        when(invoiceRepository.existsByAppointmentId(appointment.getId())).thenReturn(true);

        assertEquals(InvoiceErrorCode.DUPLICATE_INVOICE, codeOfFailure());

        verify(numberGenerator, never()).next(any());
        verify(quoteRepository, never()).findWithLinesByAppointmentId(any());
    }

    @Test
    void unknownOrNotCompletedAppointmentIsUnexpected() {
        UUID unknown = UUID.randomUUID();
        when(appointmentRepository.findWithDetailsById(unknown)).thenReturn(Optional.empty());
        assertEquals(InvoiceErrorCode.UNEXPECTED,
                assertThrows(InvoiceSkipException.class, () -> processor.process(unknown)).getCode());

        appointment.setStatus(AppointmentStatus.PLANNED);
        assertEquals(InvoiceErrorCode.UNEXPECTED, codeOfFailure());
    }
}
