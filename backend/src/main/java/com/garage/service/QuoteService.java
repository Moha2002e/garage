package com.garage.service;

import com.garage.domain.Appointment;
import com.garage.domain.Part;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.domain.QuoteStatus;
import com.garage.dto.QuoteDtos.QuoteLineRequest;
import com.garage.dto.QuoteDtos.QuoteRequest;
import com.garage.dto.QuoteDtos.QuoteResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.NotFoundException;
import com.garage.mapper.QuoteMapper;
import com.garage.repository.QuoteRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final AppointmentService appointmentService;
    private final QuoteMapper mapper;
    private final PartService partService;
    private final QuoteStockService quoteStockService;

    public QuoteService(QuoteRepository quoteRepository, AppointmentService appointmentService,
                        QuoteMapper mapper, PartService partService, QuoteStockService quoteStockService) {
        this.quoteRepository = quoteRepository;
        this.appointmentService = appointmentService;
        this.mapper = mapper;
        this.partService = partService;
        this.quoteStockService = quoteStockService;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /**
     * Empty when the appointment has no quote yet. A CLIENT only reaches the quote of their own
     * appointments: for any other appointment the access check throws a 404.
     */
    @Transactional(readOnly = true)
    public Optional<QuoteResponse> get(UUID appointmentId) {
        appointmentService.findAccessible(appointmentId);
        return quoteRepository.findWithLinesByAppointmentId(appointmentId).map(mapper::toResponse);
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    // Pas de POST : la création passe par le PUT, qui crée ou remplace les lignes.

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    /**
     * Creates the quote or replaces its lines; only possible while it is a DRAFT.
     * Part lines take their quantity out of the stock (and give it back when removed): if a part is
     * missing, nothing is saved and the error says how long the supplier needs to deliver it.
     */
    @Transactional
    public QuoteResponse replaceLines(UUID appointmentId, QuoteRequest request) {
        Appointment appointment = appointmentService.findAccessible(appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("The quote of a cancelled appointment cannot be modified");
        }
        Quote quote = quoteRepository.findWithLinesByAppointmentId(appointmentId).orElseGet(() -> {
            Quote created = new Quote();
            created.setAppointment(appointment);
            return created;
        });
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessException("Only a draft quote can be modified");
        }
        Map<UUID, Long> stockBefore = QuoteStockService.needs(quote);
        // Old lines are kept but deactivated: nothing is physically deleted.
        quote.getLines().forEach(line -> line.setActive(false));
        List<QuoteLineRequest> requested = request.lines();
        for (int i = 0; i < requested.size(); i++) {
            QuoteLineRequest line = requested.get(i);
            quote.getLines().add(mapper.toLine(line, quote, i, partOf(line)));
        }
        quoteStockService.reconcile(stockBefore, QuoteStockService.needs(quote),
                QuoteStockService.reason(appointmentId, "Quote"));
        quote.setTotal(total(quote));
        // The total may be unchanged, but the quote was modified: refresh its update date explicitly.
        quote.setUpdatedAt(Instant.now());
        return mapper.toResponse(quoteRepository.saveAndFlush(quote));
    }

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : un devis se refuse (statut REFUSED), il n'est jamais supprimé.

    // ******************************************
    // ************ PERSONNALISÉES ************
    // ******************************************

    /** DRAFT to ACCEPTED or REFUSED; both are final. */
    @Transactional
    public QuoteResponse changeStatus(UUID appointmentId, QuoteStatus target) {
        Appointment appointment = appointmentService.findAccessible(appointmentId);
        if (target != QuoteStatus.ACCEPTED && target != QuoteStatus.REFUSED) {
            throw new BusinessException("A quote can only be accepted or refused");
        }
        Quote quote = quoteRepository.findWithLinesByAppointmentId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Quote not found"));
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessException("Only a draft quote can be accepted or refused");
        }
        // A cancelled appointment already gave its parts back when it was cancelled.
        if (target == QuoteStatus.REFUSED && appointment.getStatus() != AppointmentStatus.CANCELLED) {
            quoteStockService.releaseAll(quote, QuoteStockService.reason(appointmentId, "Quote refused"));
        }
        quote.setStatus(target);
        return mapper.toResponse(quote);
    }

    /** The catalogue part of a line (null for labour), whose quantity must be a whole number. */
    private Part partOf(QuoteLineRequest line) {
        if (line.partId() == null) {
            return null;
        }
        if (line.quantity().stripTrailingZeros().scale() > 0) {
            throw new BusinessException("The quantity of a part must be a whole number");
        }
        return partService.find(line.partId());
    }

    /** Total = sum of the line totals (quantity times unit price, each rounded to 2 decimals). */
    static BigDecimal total(Quote quote) {
        return quote.getLines().stream()
                .filter(QuoteLine::isActive)
                .map(QuoteLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
