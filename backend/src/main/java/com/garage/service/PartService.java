package com.garage.service;

import com.garage.domain.Part;
import com.garage.domain.StockMovement;
import com.garage.domain.StockMovementType;
import com.garage.dto.PageResponse;
import com.garage.dto.PartDtos.PartRequest;
import com.garage.dto.PartDtos.PartResponse;
import com.garage.dto.PartDtos.StockAdjustRequest;
import com.garage.dto.PartDtos.StockMovementResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.mapper.MapperSupport;
import com.garage.mapper.PartMapper;
import com.garage.repository.PartRepository;
import com.garage.repository.StockMovementRepository;
import com.garage.specification.PartSpecifications;
import com.garage.util.PageUtil;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parts catalogue and stock. The stock only changes through {@link #adjustStock}, which writes the journal. */
@Service
public class PartService {

    private final PartRepository partRepository;
    private final StockMovementRepository movementRepository;
    private final PartMapper mapper;

    public PartService(PartRepository partRepository, StockMovementRepository movementRepository,
                       PartMapper mapper) {
        this.partRepository = partRepository;
        this.movementRepository = movementRepository;
        this.mapper = mapper;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /** Paginated list. Free text on reference and name, optional active filter, optional low-stock-only filter. */
    @Transactional(readOnly = true)
    public PageResponse<PartResponse> search(String q, Boolean active, boolean lowStockOnly, int page, int size) {
        Specification<Part> spec = Specification.where(PartSpecifications.matchesText(q))
                .and(PartSpecifications.hasActive(active));
        if (lowStockOnly) {
            spec = spec.and(PartSpecifications.lowStock());
        }
        return PageResponse.from(partRepository.findAll(spec, PageUtil.of(page, size, Sort.by("reference"))),
                mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PartResponse get(UUID id) {
        return mapper.toResponse(find(id));
    }

    /** Journal of one part, newest first (page size 1 to 50, default 10). */
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(UUID partId, int page, int size) {
        Part part = find(partId);
        return PageResponse.from(movementRepository.findByPartIdOrderByCreatedAtDesc(part.getId(),
                PageUtil.ofCapped(page, size, Sort.unsorted(), 50)), mapper::toResponse);
    }

    /** Number of active parts at or below their alert threshold (for the dashboard). */
    @Transactional(readOnly = true)
    public long countLowStock() {
        return partRepository.countLowStock();
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    /** The reference is normalized (trim, upper-case) by the mapper; a duplicate is refused with ConflictException. */
    @Transactional
    public PartResponse create(PartRequest request) {
        if (partRepository.existsByReference(MapperSupport.normalizeReference(request.reference()))) {
            throw new ConflictException("A part with this reference already exists");
        }
        checkPrice(request.unitPrice());
        return mapper.toResponse(partRepository.save(mapper.toEntity(request)));
    }

    /**
     * Applies a stock movement and writes it to the journal in the same transaction.
     * IN and OUT need a strictly positive quantity; ADJUSTMENT is a signed difference and cannot be zero.
     * Refuses a negative resulting stock with BusinessException.
     */
    @Transactional
    public PartResponse adjustStock(UUID partId, StockAdjustRequest request) {
        StockMovementType type = request.type();
        int quantity = request.quantity();
        if (type == StockMovementType.ADJUSTMENT) {
            if (quantity == 0) {
                throw new BusinessException("An adjustment cannot be zero");
            }
        } else if (quantity <= 0) {
            throw new BusinessException("Quantity must be strictly positive");
        }
        Part part = find(partId);
        applyMovement(part, type, quantity, request.reason());
        return mapper.toResponse(part);
    }

    /**
     * Takes stock out for a quote (OUT movement). Refuses an inactive part, and a missing stock with
     * the supplier lead time so the garage knows when the appointment becomes possible.
     */
    @Transactional
    public void reserve(UUID partId, int quantity, String reason) {
        Part part = find(partId);
        if (!part.isActive()) {
            throw new BusinessException("Part " + part.getReference() + " is inactive");
        }
        if (part.getQuantityInStock() < quantity) {
            String delay = part.getLeadTimeDays() > 0
                    ? ": order it, delivery in about " + part.getLeadTimeDays() + " days"
                    : ": order it";
            throw new BusinessException("Not enough stock for " + part.getReference() + " ("
                    + part.getQuantityInStock() + " available, " + quantity + " needed)" + delay);
        }
        applyMovement(part, StockMovementType.OUT, quantity, reason);
    }

    /** Puts stock back (IN movement), e.g. when a quote line is removed or the appointment is cancelled. */
    @Transactional
    public void release(UUID partId, int quantity, String reason) {
        applyMovement(find(partId), StockMovementType.IN, quantity, reason);
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    /** Never touches quantityInStock: the stock only changes through adjustStock. */
    @Transactional
    public PartResponse update(UUID id, PartRequest request) {
        Part part = find(id);
        if (partRepository.existsByReferenceAndIdNot(MapperSupport.normalizeReference(request.reference()), id)) {
            throw new ConflictException("A part with this reference already exists");
        }
        checkPrice(request.unitPrice());
        mapper.update(request, part);
        return mapper.toResponse(part);
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    /** A part is never deleted: it is activated or deactivated. */
    @Transactional
    public PartResponse setActive(UUID id, boolean active) {
        Part part = find(id);
        part.setActive(active);
        return mapper.toResponse(part);
    }

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    /** Loads a part or throws NotFoundException. */
    Part find(UUID id) {
        return partRepository.findById(id).orElseThrow(() -> new NotFoundException("Part not found"));
    }

    /** Changes the stock and writes the journal line: the only place where quantityInStock is modified. */
    private void applyMovement(Part part, StockMovementType type, int quantity, String reason) {
        int delta = type == StockMovementType.OUT ? -quantity : quantity;
        int newStock = part.getQuantityInStock() + delta;
        if (newStock < 0) {
            throw new BusinessException("Cannot reduce stock below zero");
        }
        part.setQuantityInStock(newStock);
        StockMovement movement = new StockMovement();
        movement.setPart(part);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setReason(reason);
        movementRepository.save(movement);
    }

    private void checkPrice(BigDecimal unitPrice) {
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Unit price cannot be negative");
        }
    }
}
