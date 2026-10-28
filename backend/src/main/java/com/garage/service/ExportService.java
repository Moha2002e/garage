package com.garage.service;

import com.garage.config.AppProperties;
import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceStatus;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Excel export as SpreadsheetML (.xls) rendered by FreeMarker.
 * The appointments export contains the plate and the phone number: access is restricted to ADMIN by the controller.
 */
@Service
public class ExportService {

    static final String TEXT = "text";
    static final String OK = "ok";
    static final String KO = "ko";
    static final String INFO = "info";
    static final String WARN = "warn";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Configuration freemarker;
    private final ClientService clientService;
    private final AppointmentService appointmentService;
    private final AppProperties props;
    private final InvoiceService invoiceService;

    public ExportService(Configuration freemarker, ClientService clientService,
                         AppointmentService appointmentService, AppProperties props,
                         InvoiceService invoiceService) {
        this.freemarker = freemarker;
        this.clientService = clientService;
        this.appointmentService = appointmentService;
        this.props = props;
        this.invoiceService = invoiceService;
    }

    public byte[] exportClients(String q, Boolean active) {
        List<Client> clients = clientService.searchForExport(q, active, props.exportMaxRows());
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (int i = 0; i < clients.size(); i++) {
            Client c = clients.get(i);
            rows.add(List.of(
                    cell(c.getLastName(), TEXT, i), cell(c.getFirstName(), TEXT, i),
                    cell(c.getEmail(), TEXT, i), cell(c.getPhone(), TEXT, i),
                    cell(c.isActive() ? "Active" : "Inactive", c.isActive() ? OK : KO, i)));
        }
        return render("Clients", List.of("Last name", "First name", "E-mail", "Phone", "Status"),
                List.of(24, 24, 36, 18, 14), rows);
    }

    public byte[] exportAppointments(String q, AppointmentStatus status, LocalDate from, LocalDate to) {
        List<Appointment> appointments =
                appointmentService.searchForExport(q, status, from, to, props.exportMaxRows());
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (int i = 0; i < appointments.size(); i++) {
            Appointment a = appointments.get(i);
            rows.add(List.of(
                    cell(DATE_TIME.format(a.getDate().atZone(AppointmentService.PARIS)), TEXT, i),
                    cell(a.getReason(), TEXT, i),
                    cell(a.getStatus().name(), statusStyle(a.getStatus()), i),
                    cell(a.getClient().getLastName(), TEXT, i),
                    cell(a.getClient().getFirstName(), TEXT, i),
                    cell(a.getClient().getPhone(), TEXT, i),
                    cell(a.getVehicle().getBrand(), TEXT, i),
                    cell(a.getVehicle().getModel(), TEXT, i),
                    cell(a.getVehicle().getPlate(), TEXT, i)));
        }
        return render("Appointments",
                List.of("Date", "Reason", "Status", "Last name", "First name", "Phone", "Brand", "Model", "Plate"),
                List.of(18, 40, 16, 22, 22, 18, 18, 18, 14), rows);
    }

    public byte[] exportInvoices(String q, InvoiceStatus status) {
        List<Invoice> invoices = invoiceService.searchForExport(q, status, props.exportMaxRows());
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (int i = 0; i < invoices.size(); i++) {
            Invoice inv = invoices.get(i);
            rows.add(List.of(
                    cell(inv.getNumber(), TEXT, i),
                    cell(inv.getStatus().name(), invoiceStatusStyle(inv.getStatus()), i),
                    cell(DATE_TIME.format(inv.getIssuedAt().atZone(AppointmentService.PARIS)), TEXT, i),
                    cell(inv.getPaidAt() == null ? null
                            : DATE_TIME.format(inv.getPaidAt().atZone(AppointmentService.PARIS)), TEXT, i),
                    cell(InvoiceService.clientName(inv.getClient()), TEXT, i),
                    cell(InvoiceService.vehicleLabel(inv.getAppointment().getVehicle()), TEXT, i),
                    cell(DATE_TIME.format(inv.getAppointment().getDate().atZone(AppointmentService.PARIS)), TEXT, i),
                    cell(inv.getTotal().toPlainString(), TEXT, i)));
        }
        return render("Invoices",
                List.of("Number", "Status", "Issued", "Paid", "Client", "Vehicle", "Appointment", "Total (EUR)"),
                List.of(18, 14, 18, 18, 28, 26, 18, 16), rows);
    }

    /** ISSUED orange (waiting for payment), PAID green. */
    static String invoiceStatusStyle(InvoiceStatus status) {
        return switch (status) {
            case ISSUED -> WARN;
            case PAID -> OK;
        };
    }

    /** PLANNED blue, IN_PROGRESS orange, COMPLETED green, CANCELLED red. */
    static String statusStyle(AppointmentStatus status) {
        return switch (status) {
            case PLANNED -> INFO;
            case IN_PROGRESS -> WARN;
            case COMPLETED -> OK;
            case CANCELLED -> KO;
        };
    }

    /**
     * A cell for the template: "v" is the sanitized value, "s" the style id declared in table.ftl.
     * Odd rows get the "_alt" variant of the style (light background), so rows alternate.
     */
    static Map<String, String> cell(String value, String style, int rowIndex) {
        return Map.of("v", safe(value), "s", rowIndex % 2 == 1 ? style + "_alt" : style);
    }

    private byte[] render(String sheetName, List<String> headers, List<Integer> widths,
                          List<List<Map<String, String>>> rows) {
        try (StringWriter out = new StringWriter()) {
            freemarker.getTemplate("exports/table.ftl")
                    .process(Map.of("title", "Garage", "sheetName", sheetName, "headers", headers,
                            "widths", widths, "rows", rows), out);
            // The XML declaration must be the very first characters of the file.
            return out.toString().stripLeading().getBytes(StandardCharsets.UTF_8);
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("Excel export failed", e);
        }
    }

    /**
     * Neutralizes spreadsheet formula injection: a cell starting with = + - @ (or a control character)
     * is prefixed with an apostrophe. XML escaping itself is done by the FreeMarker template.
     */
    static String safe(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
