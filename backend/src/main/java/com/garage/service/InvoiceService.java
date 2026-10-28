package com.garage.service;

import com.garage.domain.Appointment;
import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Vehicle;
import com.garage.dto.InvoiceDtos.InvoiceDetailResponse;
import com.garage.dto.InvoiceDtos.InvoiceLineResponse;
import com.garage.dto.InvoiceDtos.InvoiceResponse;
import com.garage.dto.PageResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.InvoiceRepository;
import com.garage.util.PageUtil;
import jakarta.persistence.criteria.Join;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading and paying invoices (ADMIN and EMPLOYEE, enforced by the controller). Invoices are created by the batch. */
@Service
public class InvoiceService {

    static final int MAX_QUERY_LENGTH = 100;
    private static final Sort SORT = Sort.by(Sort.Direction.DESC, "issuedAt").and(Sort.by("number"));

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /** Newest first (page size 10 or 25). {@code q} (100 characters at most) searches number, client and vehicle. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(String q, InvoiceStatus status, int page, int size) {
        Pageable pageable = PageUtil.of(page, size, SORT);
        Page<Invoice> result = invoiceRepository.findAll(spec(q, status), pageable);
        return PageResponse.from(result, InvoiceService::toResponse);
    }

    /** Same filters as the list, capped, for exports (ADMIN only, enforced by the controller). */
    @Transactional(readOnly = true)
    public List<Invoice> searchForExport(String q, InvoiceStatus status, int maxRows) {
        return invoiceRepository.findAll(spec(q, status), PageRequest.of(0, maxRows, SORT)).getContent();
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse get(UUID id) {
        return toDetail(find(id));
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    // Pas de POST : les factures sont créées par le job batch (POST /api/invoices/batch/run).

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    // Pas de PUT sur le contenu : une facture émise ne se modifie pas.

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : une facture n'est jamais supprimée.

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    /** ISSUED to PAID; an invoice that is already paid answers 409. */
    @Transactional
    public InvoiceDetailResponse pay(UUID id) {
        Invoice invoice = find(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Invoice is already paid");
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        return toDetail(invoice);
    }

    private Invoice find(UUID id) {
        return invoiceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
    }

    private static Specification<Invoice> spec(String q, InvoiceStatus status) {
        Specification<Invoice> spec = Specification.where(null);
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException("The search text cannot exceed 100 characters");
        }
        if (q != null && !q.isBlank()) {
            String like = PageUtil.likeTerm(q);
            spec = spec.and((root, query, cb) -> {
                Join<Invoice, Client> client = root.join("client");
                Join<Invoice, Appointment> appointment = root.join("appointment");
                Join<Appointment, Vehicle> vehicle = appointment.join("vehicle");
                return cb.or(
                        cb.like(cb.lower(root.get("number")), like, '\\'),
                        cb.like(cb.lower(client.get("lastName")), like, '\\'),
                        cb.like(cb.lower(client.get("firstName")), like, '\\'),
                        cb.like(cb.lower(vehicle.get("brand")), like, '\\'),
                        cb.like(cb.lower(vehicle.get("model")), like, '\\'),
                        cb.like(cb.lower(vehicle.get("plate")), like, '\\'));
            });
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return spec;
    }

    static String clientName(Client c) {
        return (c.getFirstName() + " " + c.getLastName()).trim();
    }

    static String vehicleLabel(Vehicle v) {
        return (v.getBrand() + " " + v.getModel()).trim();
    }

    static InvoiceResponse toResponse(Invoice i) {
        Appointment a = i.getAppointment();
        Client c = i.getClient();
        return new InvoiceResponse(i.getId(), i.getNumber(), i.getStatus(), a.getId(), c.getId(), clientName(c),
                vehicleLabel(a.getVehicle()), a.getDate(), i.getTotal(), i.getIssuedAt(), i.getPaidAt());
    }

    static InvoiceDetailResponse toDetail(Invoice i) {
        Appointment a = i.getAppointment();
        Client c = i.getClient();
        List<InvoiceLineResponse> lines = i.getLines().stream()
                .map(l -> new InvoiceLineResponse(l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getTotal()))
                .toList();
        return new InvoiceDetailResponse(i.getId(), i.getNumber(), i.getStatus(), a.getId(), c.getId(), clientName(c),
                vehicleLabel(a.getVehicle()), a.getDate(), i.getTotal(), i.getIssuedAt(), i.getPaidAt(), lines);
    }
}
