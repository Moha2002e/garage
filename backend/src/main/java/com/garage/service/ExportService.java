package com.garage.service;

import com.garage.config.AppProperties;
import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Client;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Excel export as SpreadsheetML (.xls) rendered by FreeMarker.
 * The appointments export contains the plate and the phone number: access is restricted to ADMIN by the controller.
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Configuration freemarker;
    private final ClientService clientService;
    private final AppointmentService appointmentService;
    private final AppProperties props;

    public ExportService(Configuration freemarker, ClientService clientService,
                         AppointmentService appointmentService, AppProperties props) {
        this.freemarker = freemarker;
        this.clientService = clientService;
        this.appointmentService = appointmentService;
        this.props = props;
    }

    public byte[] exportClients(String q, Boolean active) {
        List<Client> clients = clientService.searchForExport(q, active, props.exportMaxRows());
        List<List<String>> rows = clients.stream()
                .map(c -> List.of(
                        safe(c.getLastName()), safe(c.getFirstName()), safe(c.getEmail()), safe(c.getPhone()),
                        c.isActive() ? "Active" : "Inactive"))
                .toList();
        return render("Clients", List.of("Last name", "First name", "E-mail", "Phone", "Status"), rows);
    }

    public byte[] exportAppointments(String q, AppointmentStatus status, LocalDate from, LocalDate to) {
        List<Appointment> appointments =
                appointmentService.searchForExport(q, status, from, to, props.exportMaxRows());
        List<List<String>> rows = appointments.stream()
                .map(a -> List.of(
                        DATE_TIME.format(a.getDate().atZone(AppointmentService.PARIS)),
                        safe(a.getReason()),
                        a.getStatus().name(),
                        safe(a.getClient().getLastName()),
                        safe(a.getClient().getFirstName()),
                        safe(a.getClient().getPhone()),
                        safe(a.getVehicle().getBrand()),
                        safe(a.getVehicle().getModel()),
                        safe(a.getVehicle().getPlate())))
                .toList();
        return render("Appointments",
                List.of("Date", "Reason", "Status", "Last name", "First name", "Phone", "Brand", "Model", "Plate"),
                rows);
    }

    private byte[] render(String sheetName, List<String> headers, List<List<String>> rows) {
        try (StringWriter out = new StringWriter()) {
            freemarker.getTemplate("exports/table.ftl")
                    .process(Map.of("sheetName", sheetName, "headers", headers, "rows", rows), out);
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
