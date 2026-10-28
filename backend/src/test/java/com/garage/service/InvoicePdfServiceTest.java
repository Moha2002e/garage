package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.config.AppProperties;
import com.garage.domain.Appointment;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceLine;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Vehicle;
import com.garage.exception.NotFoundException;
import com.garage.repository.InvoiceRepository;
import com.garage.service.InvoicePdfService.PdfDocument;
import com.garage.service.InvoicePdfService.Totals;
import freemarker.template.Configuration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InvoicePdfServiceTest {

    private static final Instant ISSUED = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-03-15T09:00:00Z");

    InvoiceRepository repository;
    InvoicePdfService service;
    UUID id;

    @BeforeEach
    void setUp() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassForTemplateLoading(InvoicePdfService.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        repository = Mockito.mock(InvoiceRepository.class);
        AppProperties.Company company = new AppProperties.Company("Garage Exemple", "12 rue Exemple", "0000",
                "Ville Exemple", "Belgique", "BE0000.000.000", "BE00 0000 0000 0000", "EXAMBEBB",
                "contact@garage-exemple.invalid", "+32 00 00 00 00");
        AppProperties props = new AppProperties(null, null, null, null, null, 0, 0, null,
                new AppProperties.Garage(3, company), new AppProperties.InvoiceSettings(new BigDecimal("21")));
        service = new InvoicePdfService(cfg, repository, props);
        id = UUID.randomUUID();
    }

    private Invoice invoice(InvoiceStatus status, String firstName, String lastName) {
        Client client = new Client();
        client.setFirstName(firstName);
        client.setLastName(lastName);
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        vehicle.setPlate("AB-123-CD");
        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setVehicle(vehicle);
        appointment.setDate(Instant.parse("2026-02-27T08:00:00Z"));
        InvoiceLine l1 = line(0, "Vidange et filtre", "1", "89.90");
        InvoiceLine l2 = line(1, "Plaquettes de frein", "2", "1234.28");
        Invoice invoice = new Invoice();
        invoice.setNumber("FAC-2026-000001");
        invoice.setClient(client);
        invoice.setAppointment(appointment);
        invoice.setStatus(status);
        invoice.setIssuedAt(ISSUED);
        invoice.setPaidAt(status == InvoiceStatus.PAID ? PAID_AT : null);
        invoice.setLines(List.of(l1, l2));
        invoice.setTotal(new BigDecimal("2558.46"));
        return invoice;
    }

    private static InvoiceLine line(int order, String description, String quantity, String unitPrice) {
        InvoiceLine l = new InvoiceLine();
        l.setSortOrder(order);
        l.setDescription(description);
        l.setQuantity(new BigDecimal(quantity));
        l.setUnitPrice(new BigDecimal(unitPrice));
        l.setTotal(l.getQuantity().multiply(l.getUnitPrice()));
        return l;
    }

    @Test
    void rendersAValidPdfNamedAfterTheInvoiceNumber() {
        Mockito.when(repository.findWithDetailsById(id))
                .thenReturn(Optional.of(invoice(InvoiceStatus.ISSUED, "Jean", "Dupont")));

        PdfDocument pdf = service.generate(id);

        assertEquals("FAC-2026-000001.pdf", pdf.filename());
        assertTrue(pdf.content().length > 0);
        assertEquals("%PDF", new String(pdf.content(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void unknownInvoiceIsNotFound() {
        Mockito.when(repository.findWithDetailsById(id)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.generate(id));
    }

    @Test
    void userValuesAreEscapedAndTheDocumentStillRenders() throws Exception {
        Invoice invoice = invoice(InvoiceStatus.ISSUED, "A<b>", "Dupont & Fils");
        Mockito.when(repository.findWithDetailsById(id)).thenReturn(Optional.of(invoice));

        String xhtml = service.renderXhtml(invoice);

        assertTrue(xhtml.contains("A&lt;b&gt; Dupont &amp; Fils"));
        assertFalse(xhtml.contains("A<b>"));
        assertEquals("%PDF", new String(service.generate(id).content(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void charactersTheStandardFontCannotDrawAreReplaced() {
        assertEquals("??????", InvoicePdfService.clean("Привет"));
        assertEquals("a?b", InvoicePdfService.clean("a😀b"));
        assertEquals("Élodie Müller €", InvoicePdfService.clean("Élodie Müller €"));
        assertEquals("ab", InvoicePdfService.clean("a\u0000b"));
        Invoice invoice = invoice(InvoiceStatus.ISSUED, "Привет", "Dupont");
        Mockito.when(repository.findWithDetailsById(id)).thenReturn(Optional.of(invoice));
        assertTrue(service.generate(id).content().length > 0);
    }

    @Test
    void paidInvoiceShowsThePaymentDateAndNoBankDetails() throws Exception {
        String xhtml = service.renderXhtml(invoice(InvoiceStatus.PAID, "Jean", "Dupont"));

        assertTrue(xhtml.contains("PAYÉE le 15/03/2026"));
        assertFalse(xhtml.contains("À payer avant le"));
        assertFalse(xhtml.contains("BE00 0000 0000 0000"));
    }

    @Test
    void issuedInvoiceShowsTheDueDateAndTheBankDetails() throws Exception {
        String xhtml = service.renderXhtml(invoice(InvoiceStatus.ISSUED, "Jean", "Dupont"));

        // Issued 01/03/2026 plus 30 days.
        assertTrue(xhtml.contains("À payer avant le 31/03/2026"));
        assertFalse(xhtml.contains("PAYÉE"));
        assertTrue(xhtml.contains("BE00 0000 0000 0000"));
        assertTrue(xhtml.contains("Communication"));
        assertTrue(xhtml.contains("FAC-2026-000001"));
    }

    @Test
    void totalsAreRoundedToTwoDecimals() {
        Totals t = InvoicePdfService.totals(new BigDecimal("100.05"), new BigDecimal("21"));
        assertEquals(new BigDecimal("100.05"), t.ht());
        assertEquals(new BigDecimal("21.01"), t.vat());
        assertEquals(new BigDecimal("121.06"), t.ttc());

        Totals half = InvoicePdfService.totals(new BigDecimal("0.50"), new BigDecimal("21"));
        assertEquals(new BigDecimal("0.11"), half.vat());
        assertEquals(new BigDecimal("0.61"), half.ttc());

        Totals other = InvoicePdfService.totals(new BigDecimal("33.33"), new BigDecimal("6"));
        assertEquals(new BigDecimal("2.00"), other.vat());
        assertEquals(new BigDecimal("35.33"), other.ttc());
    }

    @Test
    void amountsDatesAndRatesUseTheEuropeanFormat() {
        assertEquals("1 234,56 €", InvoicePdfService.money(new BigDecimal("1234.56")));
        assertEquals("0,00 €", InvoicePdfService.money(BigDecimal.ZERO));
        assertEquals("2", InvoicePdfService.quantity(new BigDecimal("2.00")));
        assertEquals("1,5", InvoicePdfService.quantity(new BigDecimal("1.50")));
        assertEquals("21 %", InvoicePdfService.percent(new BigDecimal("21")));
        assertEquals("5,5 %", InvoicePdfService.percent(new BigDecimal("5.5")));
    }

    @Test
    void fileNameContainsTheInvoiceNumberOnly() {
        assertEquals("FAC-2026-000001.pdf", InvoicePdfService.filename("FAC-2026-000001"));
        assertEquals("FAC_2026____x.pdf", InvoicePdfService.filename("FAC/2026\"\r\n/x"));
        assertEquals("facture.pdf", InvoicePdfService.filename(null));
    }
}
