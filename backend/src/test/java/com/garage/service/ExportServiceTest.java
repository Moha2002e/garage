package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.config.AppProperties;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Appointment;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Vehicle;
import freemarker.template.Configuration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExportServiceTest {

    @Test
    void prefixesFormulaCharacters() {
        assertEquals("'=1+1", ExportService.safe("=1+1"));
        assertEquals("'+33612345678", ExportService.safe("+33612345678"));
        assertEquals("'-2", ExportService.safe("-2"));
        assertEquals("'@cmd", ExportService.safe("@cmd"));
    }

    @Test
    void keepsNormalValuesAndNulls() {
        assertEquals("Dupont", ExportService.safe("Dupont"));
        assertEquals("", ExportService.safe(null));
    }

    @Test
    void appointmentStatusesHaveTheirColor() {
        assertEquals("info", ExportService.statusStyle(AppointmentStatus.PLANNED));
        assertEquals("warn", ExportService.statusStyle(AppointmentStatus.IN_PROGRESS));
        assertEquals("ok", ExportService.statusStyle(AppointmentStatus.COMPLETED));
        assertEquals("ko", ExportService.statusStyle(AppointmentStatus.CANCELLED));
    }

    @Test
    void oddRowsUseTheAlternateStyleAndValuesAreSanitized() {
        assertEquals("text", ExportService.cell("a", "text", 0).get("s"));
        assertEquals("text_alt", ExportService.cell("a", "text", 1).get("s"));
        assertEquals("ok_alt", ExportService.cell("a", "ok", 3).get("s"));
        assertEquals("'=1+1", ExportService.cell("=1+1", "text", 0).get("v"));
        assertEquals("", ExportService.cell(null, "text", 0).get("v"));
    }

    @Test
    void clientsExportIsStyledSpreadsheet() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassForTemplateLoading(ExportService.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        ClientService clientService = Mockito.mock(ClientService.class);
        Client active = new Client();
        active.setLastName("Dupont");
        active.setFirstName("Jean");
        active.setEmail("jean@example.com");
        active.setPhone("0600000000");
        Client inactive = new Client();
        inactive.setLastName("Martin");
        inactive.setFirstName("Luc <b>");
        inactive.setEmail("luc@example.com");
        inactive.setActive(false);
        Mockito.when(clientService.searchForExport(null, null, 10)).thenReturn(List.of(active, inactive));
        AppProperties props = new AppProperties(null, null, null, null, null, 0, 10, null, null, null);
        var service = new ExportService(cfg, clientService, Mockito.mock(AppointmentService.class), props,
                Mockito.mock(InvoiceService.class));

        String xml = new String(service.exportClients(null, null), StandardCharsets.UTF_8);

        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("ss:StyleID=\"title\""));
        assertTrue(xml.contains("ss:StyleID=\"header\""));
        assertTrue(xml.contains("ss:StyleID=\"ok\"><Data ss:Type=\"String\">Active<"));
        assertTrue(xml.contains("ss:StyleID=\"ko_alt\"><Data ss:Type=\"String\">Inactive<"));
        assertTrue(xml.contains("ss:StyleID=\"text_alt\""));
        assertTrue(xml.contains("Luc &lt;b&gt;"));
    }

    @Test
    void invoiceStatusesHaveTheirColor() {
        assertEquals("warn", ExportService.invoiceStatusStyle(InvoiceStatus.ISSUED));
        assertEquals("ok", ExportService.invoiceStatusStyle(InvoiceStatus.PAID));
    }

    @Test
    void invoicesExportColorsTheStatuses() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassForTemplateLoading(ExportService.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        InvoiceService invoiceService = Mockito.mock(InvoiceService.class);
        Mockito.when(invoiceService.searchForExport(null, null, 10))
                .thenReturn(List.of(invoice("FAC-2026-000001", InvoiceStatus.ISSUED, null),
                        invoice("FAC-2026-000002", InvoiceStatus.PAID, Instant.parse("2026-10-22T10:00:00Z"))));
        AppProperties props = new AppProperties(null, null, null, null, null, 0, 10, null, null, null);
        var service = new ExportService(cfg, Mockito.mock(ClientService.class),
                Mockito.mock(AppointmentService.class), props, invoiceService);

        String xml = new String(service.exportInvoices(null, null), StandardCharsets.UTF_8);

        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("ss:Name=\"Invoices\""));
        assertTrue(xml.contains("ss:StyleID=\"text\"><Data ss:Type=\"String\">FAC-2026-000001<"));
        assertTrue(xml.contains("ss:StyleID=\"warn\"><Data ss:Type=\"String\">ISSUED<"));
        assertTrue(xml.contains("ss:StyleID=\"ok_alt\"><Data ss:Type=\"String\">PAID<"));
        assertTrue(xml.contains("Jean Dupont"));
        assertTrue(xml.contains("Renault Clio"));
        assertTrue(xml.contains(">91.00<"));
        assertTrue(xml.contains("22/10/2026 12:00"));
    }

    private static Invoice invoice(String number, InvoiceStatus status, Instant paidAt) {
        Client client = new Client();
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setVehicle(vehicle);
        appointment.setDate(Instant.parse("2026-10-20T12:30:00Z"));
        Invoice invoice = new Invoice();
        invoice.setNumber(number);
        invoice.setStatus(status);
        invoice.setClient(client);
        invoice.setAppointment(appointment);
        invoice.setTotal(new BigDecimal("91.00"));
        invoice.setIssuedAt(Instant.parse("2026-10-21T08:00:00Z"));
        invoice.setPaidAt(paidAt);
        return invoice;
    }
}
