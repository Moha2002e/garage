package com.garage.service;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.domain.QuoteStatus;
import com.garage.dto.QuoteDtos.QuoteLineRequest;
import com.garage.dto.QuoteDtos.QuoteLineResponse;
import com.garage.dto.QuoteDtos.QuoteRequest;
import com.garage.dto.QuoteDtos.QuoteResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.NotFoundException;
import com.garage.repository.QuoteRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final AppointmentService appointmentService;

    public QuoteService(QuoteRepository quoteRepository, AppointmentService appointmentService) {
        this.quoteRepository = quoteRepository;
        this.appointmentService = appointmentService;
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
        return quoteRepository.findWithLinesByAppointmentId(appointmentId).map(QuoteService::toResponse);
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    // Pas de POST : la création passe par le PUT, qui crée ou remplace les lignes.

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    /** Creates the quote or replaces its lines; only possible while it is a DRAFT. */
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
        // Old lines are kept but deactivated: nothing is physically deleted.
        quote.getLines().forEach(line -> line.setActive(false));
        List<QuoteLineRequest> requested = request.lines();
        for (int i = 0; i < requested.size(); i++) {
            quote.getLines().add(newLine(quote, requested.get(i), i));
        }
        quote.setTotal(total(quote));
        // The total may be unchanged, but the quote was modified: refresh its update date explicitly.
        quote.setUpdatedAt(Instant.now());
        return toResponse(quoteRepository.saveAndFlush(quote));
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
        appointmentService.findAccessible(appointmentId);
        if (target != QuoteStatus.ACCEPTED && target != QuoteStatus.REFUSED) {
            throw new BusinessException("A quote can only be accepted or refused");
        }
        Quote quote = quoteRepository.findWithLinesByAppointmentId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Quote not found"));
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessException("Only a draft quote can be accepted or refused");
        }
        quote.setStatus(target);
        return toResponse(quote);
    }

    private QuoteLine newLine(Quote quote, QuoteLineRequest request, int order) {
        QuoteLine line = new QuoteLine();
        line.setQuote(quote);
        line.setSortOrder(order);
        line.setDescription(request.description().trim());
        line.setQuantity(request.quantity());
        line.setUnitPrice(request.unitPrice());
        return line;
    }

    /** Total = sum of the line totals (quantity times unit price, each rounded to 2 decimals). */
    static BigDecimal total(Quote quote) {
        return quote.getLines().stream()
                .filter(QuoteLine::isActive)
                .map(QuoteLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    static QuoteResponse toResponse(Quote quote) {
        List<QuoteLineResponse> lines = quote.getLines().stream()
                .filter(QuoteLine::isActive)
                .map(l -> new QuoteLineResponse(l.getId(), l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                        l.lineTotal()))
                .toList();
        return new QuoteResponse(quote.getId(), quote.getAppointment().getId(), quote.getStatus(), lines,
                quote.getTotal(), quote.getUpdatedAt());
    }
}
