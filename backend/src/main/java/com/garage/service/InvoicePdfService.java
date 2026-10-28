package com.garage.service;

import com.garage.config.AppProperties;
import com.garage.domain.Appointment;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceLine;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Vehicle;
import com.garage.exception.NotFoundException;
import com.garage.exception.PdfGenerationException;
import com.garage.repository.InvoiceRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PDF of an invoice: the FreeMarker template invoices/invoice.ftl produces well formed XHTML (automatic XML
 * escaping), which openhtmltopdf turns into an A4 PDF with the standard Helvetica font (no font file).
 * Amounts, dates and statuses are formatted here (French style), so the template only places values.
 * The invoice content is personal data: nothing of it is logged.
 */
@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);

    static final String TEMPLATE = "invoices/invoice.ftl";
    static final String LOGO_RESOURCE = "/templates/invoices/logo.png";
    /** Payment term in days after the issue date. */
    static final int DUE_DAYS = 30;
    static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("21");

    private static final ZoneId PARIS = AppointmentService.PARIS;
    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", FR);
    /** Characters the standard PDF fonts (WinAnsi) can draw. */
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final char NBSP = ' ';

    /** The file name is the invoice number only, never a client or vehicle value. */
    public record PdfDocument(String filename, byte[] content) {
    }

    /** Amounts of an invoice: ht is the invoice total (quotes are excluding VAT). */
    public record Totals(BigDecimal ht, BigDecimal vat, BigDecimal ttc) {
    }

    private final Configuration freemarker;
    private final InvoiceRepository invoiceRepository;
    private final AppProperties props;
    private final String logoDataUri;

    public InvoicePdfService(Configuration freemarker, InvoiceRepository invoiceRepository, AppProperties props) {
        this.freemarker = freemarker;
        this.invoiceRepository = invoiceRepository;
        this.props = props;
        this.logoDataUri = loadLogo();
    }

    /** @throws NotFoundException unknown invoice (404); PdfGenerationException when the rendering fails (500) */
    @Transactional(readOnly = true)
    public PdfDocument generate(UUID id) {
        Invoice invoice = invoiceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
        try {
            byte[] pdf = toPdf(renderXhtml(invoice));
            return new PdfDocument(filename(invoice.getNumber()), pdf);
        } catch (IOException | TemplateException | RuntimeException e) {
            // Only the exception type: messages of the renderers can quote the content.
            log.error("Invoice PDF rendering failed ({})", e.getClass().getSimpleName());
            throw new PdfGenerationException();
        }
    }

    // ******************************************
    // ************ Rendering ************
    // ******************************************

    String renderXhtml(Invoice invoice) throws IOException, TemplateException {
        try (StringWriter out = new StringWriter()) {
            freemarker.getTemplate(TEMPLATE, FR).process(model(invoice), out);
            return out.toString().strip();
        }
    }

    byte[] toPdf(String xhtml) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    private Map<String, Object> model(Invoice invoice) {
        Client client = invoice.getClient();
        Appointment appointment = invoice.getAppointment();
        Vehicle vehicle = appointment.getVehicle();
        boolean paid = invoice.getStatus() == InvoiceStatus.PAID;
        LocalDate issued = invoice.getIssuedAt().atZone(PARIS).toLocalDate();
        BigDecimal vatRate = vatRate();
        Totals totals = totals(invoice.getTotal(), vatRate);

        Map<String, Object> inv = new HashMap<>();
        inv.put("number", clean(invoice.getNumber()));
        inv.put("issueDate", DATE.format(issued));
        inv.put("dueDate", DATE.format(issued.plusDays(DUE_DAYS)));
        inv.put("paid", paid);
        inv.put("statusLabel", paid ? "Payée" : "À payer");
        inv.put("paidDate", paid && invoice.getPaidAt() != null ? date(invoice.getPaidAt()) : "");
        inv.put("clientName", clean(InvoiceService.clientName(client)));
        inv.put("vehicleLabel", clean(InvoiceService.vehicleLabel(vehicle)));
        inv.put("plate", clean(vehicle.getPlate()));
        inv.put("appointmentDate", date(appointment.getDate()));

        List<Map<String, String>> lines = new ArrayList<>();
        for (InvoiceLine l : invoice.getLines()) {
            lines.add(Map.of(
                    "description", clean(l.getDescription()),
                    "quantity", quantity(l.getQuantity()),
                    "unitPrice", money(l.getUnitPrice()),
                    "total", money(l.getTotal())));
        }

        Map<String, String> sums = Map.of(
                "ht", money(totals.ht()),
                "vatRate", percent(vatRate),
                "vat", money(totals.vat()),
                "ttc", money(totals.ttc()));

        Map<String, Object> model = new HashMap<>();
        model.put("company", companyModel());
        model.put("inv", inv);
        model.put("lines", lines);
        model.put("totals", sums);
        if (logoDataUri != null) {
            model.put("logo", logoDataUri);
        }
        return model;
    }

    private Map<String, String> companyModel() {
        AppProperties.Company c = props.garage() == null ? null : props.garage().company();
        if (c == null) {
            c = new AppProperties.Company(null, null, null, null, null, null, null, null, null, null);
        }
        Map<String, String> m = new HashMap<>();
        m.put("name", clean(c.name()));
        m.put("address", clean(c.address()));
        m.put("postalCity", clean(((c.postalCode() == null ? "" : c.postalCode()) + " "
                + (c.city() == null ? "" : c.city()))));
        m.put("country", clean(c.country()));
        m.put("vatNumber", clean(c.vatNumber()));
        m.put("iban", clean(c.iban()));
        m.put("bic", clean(c.bic()));
        m.put("email", clean(c.email()));
        m.put("phone", clean(c.phone()));
        return m;
    }

    private BigDecimal vatRate() {
        return props.invoice() == null || props.invoice().vatRate() == null
                ? DEFAULT_VAT_RATE : props.invoice().vatRate();
    }

    // ******************************************
    // ************ Pure helpers (unit tested) ************
    // ******************************************

    /** HT = invoice total, VAT = HT times rate / 100 and TTC = HT + VAT, each rounded to 2 decimals (HALF_UP). */
    static Totals totals(BigDecimal total, BigDecimal vatRate) {
        BigDecimal ht = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = ht.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Totals(ht, vat, ht.add(vat));
    }

    /** European format with a non breaking space as thousands separator: "1 234,56 €". */
    static String money(BigDecimal value) {
        return format("#,##0.00", value) + NBSP + "€";
    }

    /** "2" for 2.00 and "1,5" for 1.50. */
    static String quantity(BigDecimal value) {
        return format("#,##0.##", value);
    }

    /** "21 %" for 21 and "5,5 %" for 5.5. */
    static String percent(BigDecimal rate) {
        return format("0.##", rate) + NBSP + "%";
    }

    private static String format(String pattern, BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(FR);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(NBSP);
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    private static String date(Instant instant) {
        return DATE.format(instant.atZone(PARIS).toLocalDate());
    }

    /**
     * Text safe for the XHTML parser and the standard PDF fonts: control characters are dropped, characters that
     * Helvetica cannot draw (other alphabets, emoji) become "?". XML escaping itself is done by the template.
     */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        CharsetEncoder encoder = WIN_ANSI.newEncoder();
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp == '\t' || cp == '\n' || cp == '\r') {
                sb.append(' ');
            } else if (cp < 0x20 || cp == 0x7F) {
                return;
            } else if (cp <= 0xFFFF && encoder.canEncode((char) cp)) {
                sb.appendCodePoint(cp);
            } else {
                sb.append('?');
            }
        });
        return sb.toString().strip();
    }

    /** The invoice number (FAC-2026-000001) with any other character replaced: nothing else reaches the header. */
    static String filename(String number) {
        String safe = number == null ? "" : number.replaceAll("[^A-Za-z0-9_-]", "_");
        return (safe.isBlank() ? "facture" : safe) + ".pdf";
    }

    private static String loadLogo() {
        try (InputStream in = InvoicePdfService.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) {
                return null;
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }
}
