package com.garage.batch;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceErrorCode;
import com.garage.domain.InvoiceLine;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.domain.QuoteStatus;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.InvoiceRepository;
import com.garage.repository.QuoteRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Builds the invoice of one appointment (item = appointment id). Every business problem is reported with an
 * {@link InvoiceSkipException}; the invoice number is taken last, so an invalid appointment never uses a number.
 */
@Component
public class InvoiceProcessor implements ItemProcessor<UUID, Invoice> {

    /** The column is numeric(12,2): 10 digits before the decimal point at most. */
    static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");

    private final AppointmentRepository appointmentRepository;
    private final QuoteRepository quoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator numberGenerator;

    public InvoiceProcessor(AppointmentRepository appointmentRepository, QuoteRepository quoteRepository,
                            InvoiceRepository invoiceRepository, InvoiceNumberGenerator numberGenerator) {
        this.appointmentRepository = appointmentRepository;
        this.quoteRepository = quoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.numberGenerator = numberGenerator;
    }

    @Override
    public Invoice process(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(() -> new InvoiceSkipException(InvoiceErrorCode.UNEXPECTED, "Appointment not found"));
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new InvoiceSkipException(InvoiceErrorCode.UNEXPECTED, "Appointment is not completed");
        }
        if (invoiceRepository.existsByAppointmentId(appointmentId)) {
            throw new InvoiceSkipException(InvoiceErrorCode.DUPLICATE_INVOICE, "Appointment already has an invoice");
        }
        Quote quote = quoteRepository.findWithLinesByAppointmentId(appointmentId)
                .filter(q -> q.getStatus() == QuoteStatus.ACCEPTED)
                .orElseThrow(() -> new InvoiceSkipException(InvoiceErrorCode.NO_ACCEPTED_QUOTE,
                        "Appointment has no accepted quote"));
        List<QuoteLine> lines = validatedLines(quote);
        Instant now = Instant.now();
        return assemble(appointment, quote, lines, numberGenerator.next(now), now);
    }

    /**
     * Active lines of the quote in display order, after the amount checks.
     * EMPTY_QUOTE: no active line. INVALID_AMOUNT: quantity not above 0, negative unit price, total not above 0,
     * total too large for the column, or a quote total that differs from the sum of its lines.
     */
    static List<QuoteLine> validatedLines(Quote quote) {
        List<QuoteLine> lines = quote.getLines().stream()
                .filter(QuoteLine::isActive)
                .sorted(Comparator.comparingInt(QuoteLine::getSortOrder))
                .toList();
        if (lines.isEmpty()) {
            throw new InvoiceSkipException(InvoiceErrorCode.EMPTY_QUOTE, "Accepted quote has no line");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (QuoteLine line : lines) {
            if (line.getQuantity() == null || line.getQuantity().signum() <= 0
                    || line.getUnitPrice() == null || line.getUnitPrice().signum() < 0) {
                throw new InvoiceSkipException(InvoiceErrorCode.INVALID_AMOUNT, "A quote line has an invalid amount");
            }
            total = total.add(line.lineTotal());
        }
        total = total.setScale(2, java.math.RoundingMode.HALF_UP);
        if (total.signum() <= 0) {
            throw new InvoiceSkipException(InvoiceErrorCode.INVALID_AMOUNT, "The invoice total must be above 0");
        }
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new InvoiceSkipException(InvoiceErrorCode.INVALID_AMOUNT, "The invoice total is too large");
        }
        if (quote.getTotal() == null || total.compareTo(quote.getTotal()) != 0) {
            throw new InvoiceSkipException(InvoiceErrorCode.INVALID_AMOUNT,
                    "The quote total differs from the sum of its lines");
        }
        return lines;
    }

    /** Copies the (already validated) lines; the invoice total is the sum of the copied line totals. */
    static Invoice assemble(Appointment appointment, Quote quote, List<QuoteLine> lines, String number, Instant now) {
        Invoice invoice = new Invoice();
        invoice.setNumber(number);
        invoice.setAppointment(appointment);
        invoice.setClient(appointment.getClient());
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(now);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            QuoteLine source = lines.get(i);
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setSortOrder(i);
            line.setDescription(source.getDescription());
            line.setQuantity(source.getQuantity());
            line.setUnitPrice(source.getUnitPrice());
            line.setTotal(source.lineTotal());
            invoice.getLines().add(line);
            total = total.add(line.getTotal());
        }
        invoice.setTotal(total.setScale(2, java.math.RoundingMode.HALF_UP));
        return invoice;
    }
}
