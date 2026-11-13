package com.garage.service;

import com.garage.domain.Client;
import com.garage.domain.Invoice;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.Vehicle;
import com.garage.dto.InvoiceDtos.InvoiceDetailResponse;
import com.garage.dto.InvoiceDtos.InvoiceResponse;
import com.garage.dto.PageResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.mapper.InvoiceMapper;
import com.garage.mapper.MapperSupport;
import com.garage.repository.InvoiceRepository;
import com.garage.specification.InvoiceSpecifications;
import com.garage.util.PageUtil;
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
    private final InvoiceMapper mapper;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceMapper mapper) {
        this.invoiceRepository = invoiceRepository;
        this.mapper = mapper;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /** Newest first (page size 10 or 25). {@code q} (100 characters at most) searches number, client and vehicle. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(String q, InvoiceStatus status, int page, int size) {
        Pageable pageable = PageUtil.of(page, size, SORT);
        Page<Invoice> result = invoiceRepository.findAll(spec(q, status), pageable);
        return PageResponse.from(result, mapper::toResponse);
    }

    /** Same filters as the list, capped, for exports (ADMIN only, enforced by the controller). */
    @Transactional(readOnly = true)
    public List<Invoice> searchForExport(String q, InvoiceStatus status, int maxRows) {
        return invoiceRepository.findAll(spec(q, status), PageRequest.of(0, maxRows, SORT)).getContent();
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse get(UUID id) {
        return mapper.toDetail(find(id));
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
        return mapper.toDetail(invoice);
    }

    private Invoice find(UUID id) {
        return invoiceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
    }

    private static Specification<Invoice> spec(String q, InvoiceStatus status) {
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException("The search text cannot exceed 100 characters");
        }
        return Specification.where(InvoiceSpecifications.matchesText(q))
                .and(InvoiceSpecifications.hasStatus(status));
    }

    static String clientName(Client c) {
        return MapperSupport.join(c.getFirstName(), c.getLastName());
    }

    static String vehicleLabel(Vehicle v) {
        return MapperSupport.join(v.getBrand(), v.getModel());
    }
}
