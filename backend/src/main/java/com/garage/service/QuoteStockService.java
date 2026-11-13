package com.garage.service;

import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.domain.QuoteStatus;
import com.garage.exception.BusinessException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the stock in line with the part lines of a quote. A quote holds its parts as long as it is not
 * refused and its appointment is not cancelled: the stock goes down when the lines are saved, and comes
 * back when a line disappears, the quote is refused or the appointment is cancelled.
 */
@Service
public class QuoteStockService {

    // Safety cap, not a business figure: no real repair order needs this many units of one part.
    // Catches a mistaken or malicious quantity before it is cast down to int for PartService.
    private static final long MAX_REASONABLE_QUANTITY = 100_000;

    private final PartService partService;

    public QuoteStockService(PartService partService) {
        this.partService = partService;
    }

    /**
     * Quantity needed per part over the active part lines of the quote (same part on two lines: summed).
     * Summed as long: a quote can have up to 50 lines of up to ~10^8 units each, which overflows an int.
     */
    static Map<UUID, Long> needs(Quote quote) {
        Map<UUID, Long> needs = new HashMap<>();
        for (QuoteLine line : quote.getLines()) {
            if (line.isActive() && line.getPart() != null) {
                needs.merge(line.getPart().getId(), wholeQuantity(line.getQuantity()), Long::sum);
            }
        }
        return needs;
    }

    /** A part is counted in units: 1.5 of a part makes no sense. */
    static long wholeQuantity(BigDecimal quantity) {
        return quantity.longValueExact();
    }

    /** Applies the difference between the needs before and after a change of the quote lines. */
    @Transactional
    public void reconcile(Map<UUID, Long> before, Map<UUID, Long> after, String reason) {
        Set<UUID> parts = new HashSet<>(before.keySet());
        parts.addAll(after.keySet());
        // Releases first, so a quantity moved from one line to another never needs extra stock.
        for (UUID partId : parts) {
            long delta = after.getOrDefault(partId, 0L) - before.getOrDefault(partId, 0L);
            if (delta < 0) {
                partService.release(partId, toSafeInt(-delta), reason);
            }
        }
        for (UUID partId : parts) {
            long delta = after.getOrDefault(partId, 0L) - before.getOrDefault(partId, 0L);
            if (delta > 0) {
                partService.reserve(partId, toSafeInt(delta), reason);
            }
        }
    }

    /** Refuses an unrealistic quantity instead of silently truncating it when narrowing to int. */
    private static int toSafeInt(long quantity) {
        if (quantity > MAX_REASONABLE_QUANTITY) {
            throw new BusinessException("Quantity is unrealistically large (max " + MAX_REASONABLE_QUANTITY + ")");
        }
        return (int) quantity;
    }

    /** Gives back everything the quote holds, unless it is already refused (refusal released it). */
    @Transactional
    public void releaseAll(Quote quote, String reason) {
        if (quote.getStatus() == QuoteStatus.REFUSED) {
            return;
        }
        reconcile(needs(quote), Map.<UUID, Long>of(), reason);
    }

    static String reason(UUID appointmentId, String action) {
        return action + " - appointment " + appointmentId;
    }
}
