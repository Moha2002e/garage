package com.garage.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.AbstractIntegrationTest;
import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.BatchError;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceErrorCode;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.domain.QuoteStatus;
import com.garage.domain.Vehicle;
import com.garage.dto.InvoiceDtos.BatchRunResponse;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.BatchErrorRepository;
import com.garage.repository.ClientRepository;
import com.garage.repository.InvoiceRepository;
import com.garage.repository.QuoteRepository;
import com.garage.repository.VehicleRepository;
import com.garage.service.InvoiceBatchService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

/**
 * The real job on a real PostgreSQL. The database may contain data of other tests, so every assertion looks at the
 * appointments created here, never at global counts. Year 2032 is unused by other tests.
 */
class InvoiceBatchIT extends AbstractIntegrationTest {

    @Autowired ClientRepository clientRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired QuoteRepository quoteRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired BatchErrorRepository batchErrorRepository;
    @Autowired InvoiceBatchService batchService;

    private Client client;
    private Vehicle vehicle;
    private int day = 1;

    private void createOwner(String suffix) {
        client = new Client();
        client.setLastName("Batch");
        client.setFirstName("Invoice");
        client.setEmail("batch-" + suffix + "@example.com");
        clientRepository.save(client);
        vehicle = new Vehicle();
        vehicle.setClient(client);
        vehicle.setPlate(suffix);
        vehicle.setBrand("Brand");
        vehicle.setModel("Model");
        vehicleRepository.save(vehicle);
    }

    private Appointment appointment(AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setClient(client);
        a.setVehicle(vehicle);
        a.setDate(Instant.parse("2032-05-" + String.format("%02d", day++) + "T08:00:00Z"));
        a.setReason("Invoice batch test");
        a.setStatus(status);
        return appointmentRepository.save(a);
    }

    /** A quote with the given status and one active line; total is stored as given (may be inconsistent on purpose). */
    private void quote(Appointment a, QuoteStatus status, String total, String quantity, String unitPrice) {
        Quote quote = new Quote();
        quote.setAppointment(a);
        quote.setStatus(status);
        quote.setTotal(new BigDecimal(total));
        if (quantity != null) {
            QuoteLine line = new QuoteLine();
            line.setQuote(quote);
            line.setSortOrder(0);
            line.setDescription("Work");
            line.setQuantity(new BigDecimal(quantity));
            line.setUnitPrice(new BigDecimal(unitPrice));
            quote.getLines().add(line);
        }
        quoteRepository.save(quote);
    }

    private List<BatchError> errorsOf(long runId, UUID appointmentId) {
        return batchErrorRepository.findByRunId(runId, Pageable.unpaged()).getContent().stream()
                .filter(e -> appointmentId.equals(e.getAppointmentId())).toList();
    }

    @Test
    void invoicesWhatCanBeInvoicedRecordsTheRestAndNeverInvoicesTwice() {
        createOwner("BT-001-IT");
        Appointment valid = appointment(AppointmentStatus.COMPLETED);
        quote(valid, QuoteStatus.ACCEPTED, "91.00", "2", "45.50");
        Appointment empty = appointment(AppointmentStatus.COMPLETED);
        quote(empty, QuoteStatus.ACCEPTED, "0.00", null, null);
        Appointment invalid = appointment(AppointmentStatus.COMPLETED);
        quote(invalid, QuoteStatus.ACCEPTED, "-5.00", "1", "-5");
        Appointment draft = appointment(AppointmentStatus.COMPLETED);
        quote(draft, QuoteStatus.DRAFT, "10.00", "1", "10");
        Appointment planned = appointment(AppointmentStatus.PLANNED);
        quote(planned, QuoteStatus.ACCEPTED, "10.00", "1", "10");
        Appointment withoutQuote = appointment(AppointmentStatus.COMPLETED);

        BatchRunResponse first = batchService.run();

        assertEquals("COMPLETED", first.status());
        assertTrue(first.writeCount() >= 1);
        assertTrue(first.skipCount() >= 2);
        Optional<Invoice> invoice = invoiceRepository.findWithLinesByAppointmentId(valid.getId());
        assertTrue(invoice.isPresent());
        assertTrue(invoice.get().getNumber().matches("FAC-\\d{4}-\\d{6}"));
        assertEquals(new BigDecimal("91.00"), invoice.get().getTotal());
        assertEquals(InvoiceStatus.ISSUED, invoice.get().getStatus());
        assertEquals(1, invoice.get().getLines().size());
        assertEquals(new BigDecimal("91.00"), invoice.get().getLines().get(0).getTotal());
        assertEquals(InvoiceErrorCode.EMPTY_QUOTE, errorsOf(first.id(), empty.getId()).get(0).getCode());
        assertEquals(InvoiceErrorCode.INVALID_AMOUNT, errorsOf(first.id(), invalid.getId()).get(0).getCode());
        for (Appointment ignored : List.of(empty, invalid, draft, planned, withoutQuote)) {
            assertFalse(invoiceRepository.existsByAppointmentId(ignored.getId()));
        }
        for (Appointment notCandidate : List.of(valid, draft, planned, withoutQuote)) {
            assertTrue(errorsOf(first.id(), notCandidate.getId()).isEmpty());
        }

        // Second run: the valid appointment is not selected any more, the invalid ones fail again.
        BatchRunResponse second = batchService.run();

        assertEquals("COMPLETED", second.status());
        assertEquals(0, second.writeCount());
        assertEquals(invoice.get().getId(),
                invoiceRepository.findWithLinesByAppointmentId(valid.getId()).orElseThrow().getId());
        assertEquals(1, errorsOf(second.id(), empty.getId()).size());
        assertTrue(errorsOf(second.id(), valid.getId()).isEmpty());
    }

    @Test
    void theDatabaseRefusesASecondInvoiceForTheSameAppointment() {
        createOwner("BT-002-IT");
        Appointment a = appointment(AppointmentStatus.COMPLETED);
        quote(a, QuoteStatus.ACCEPTED, "10.00", "1", "10");
        Quote quote = quoteRepository.findWithLinesByAppointmentId(a.getId()).orElseThrow();
        Appointment loaded = appointmentRepository.findWithDetailsById(a.getId()).orElseThrow();

        invoiceRepository.saveAndFlush(InvoiceProcessor.assemble(loaded, quote,
                InvoiceProcessor.validatedLines(quote), "FAC-2032-900001", Instant.now()));
        assertTrue(invoiceRepository.findInvoiceableIds().stream().noneMatch(id -> id.equals(a.getId())));

        Invoice duplicate = InvoiceProcessor.assemble(loaded, quote,
                InvoiceProcessor.validatedLines(quote), "FAC-2032-900002", Instant.now());
        assertThrows(DataIntegrityViolationException.class, () -> invoiceRepository.saveAndFlush(duplicate));
    }
}
