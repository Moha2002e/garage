package com.garage.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.AbstractIntegrationTest;
import com.garage.domain.Part;
import com.garage.specification.PartSpecifications;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Part queries on a real PostgreSQL: the Specifications, the low-stock count and the unique reference. */
class PartRepositoryIT extends AbstractIntegrationTest {

    @Autowired PartRepository partRepository;

    // Every test uses its own reference prefix, so tests do not see each other's parts.
    private String prefix() {
        return "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-";
    }

    private Part save(String reference, String name, int stock, int threshold, boolean active) {
        Part part = new Part();
        part.setReference(reference);
        part.setName(name);
        part.setUnitPrice(new BigDecimal("10.00"));
        part.setQuantityInStock(stock);
        part.setLowStockThreshold(threshold);
        part.setActive(active);
        return partRepository.saveAndFlush(part);
    }

    private List<String> references(Specification<Part> spec) {
        return partRepository.findAll(spec, Sort.by("reference")).stream().map(Part::getReference).toList();
    }

    @Test
    void textSearchMatchesReferenceOrNameIgnoringCase() {
        String p = prefix();
        save(p + "BRK", "Plaquettes de frein", 5, 1, true);
        save(p + "OIL", "Huile moteur", 5, 1, true);
        Specification<Part> mine = PartSpecifications.matchesText(p.toLowerCase());
        assertEquals(List.of(p + "BRK", p + "OIL"), references(mine));
        assertEquals(List.of(p + "BRK"), references(
                Specification.where(mine).and(PartSpecifications.matchesText("PLAQUETTES"))));
        assertEquals(List.of(p + "OIL"), references(
                Specification.where(mine).and(PartSpecifications.matchesText(p + "oil"))));
    }

    @Test
    void textSearchTreatsPercentAndUnderscoreAsPlainCharacters() {
        String p = prefix();
        save(p + "A", "Remise 10% gratuite", 5, 1, true);
        save(p + "B", "Remise 10 euros", 5, 1, true);
        assertEquals(List.of(p + "A"), references(
                Specification.where(PartSpecifications.matchesText(p)).and(PartSpecifications.matchesText("10%"))));
    }

    @Test
    void blankTextAndNullStatusMatchEverything() {
        String p = prefix();
        save(p + "A", "A", 5, 1, true);
        save(p + "B", "B", 5, 1, false);
        Specification<Part> mine = PartSpecifications.matchesText(p);
        assertEquals(2, references(Specification.where(mine).and(PartSpecifications.matchesText("  "))
                .and(PartSpecifications.hasActive(null))).size());
    }

    @Test
    void activeFilterSeparatesActiveFromInactive() {
        String p = prefix();
        save(p + "A", "Active", 5, 1, true);
        save(p + "B", "Inactive", 5, 1, false);
        Specification<Part> mine = PartSpecifications.matchesText(p);
        assertEquals(List.of(p + "A"), references(Specification.where(mine).and(PartSpecifications.hasActive(true))));
        assertEquals(List.of(p + "B"), references(Specification.where(mine).and(PartSpecifications.hasActive(false))));
    }

    @Test
    void lowStockIsAtOrBelowTheThreshold() {
        String p = prefix();
        save(p + "BELOW", "Below", 1, 3, true);
        save(p + "EQUAL", "Equal", 3, 3, true);
        save(p + "ABOVE", "Above", 4, 3, true);
        save(p + "ZERO", "Zero threshold, empty", 0, 0, true);
        assertEquals(List.of(p + "BELOW", p + "EQUAL", p + "ZERO"), references(
                Specification.where(PartSpecifications.matchesText(p)).and(PartSpecifications.lowStock())));
    }

    @Test
    void countLowStockIgnoresInactiveParts() {
        long before = partRepository.countLowStock();
        String p = prefix();
        save(p + "LOW", "Low", 0, 2, true);
        save(p + "LOWOFF", "Low but inactive", 0, 2, false);
        save(p + "OK", "Fine", 9, 2, true);
        assertEquals(before + 1, partRepository.countLowStock());
    }

    @Test
    void referenceExistenceChecksExcludeThePartItself() {
        String p = prefix();
        Part one = save(p + "ONE", "One", 5, 1, true);
        Part two = save(p + "TWO", "Two", 5, 1, true);
        assertTrue(partRepository.existsByReference(p + "ONE"));
        assertFalse(partRepository.existsByReference(p + "NOPE"));
        assertFalse(partRepository.existsByReferenceAndIdNot(p + "ONE", one.getId()));
        assertTrue(partRepository.existsByReferenceAndIdNot(p + "ONE", two.getId()));
    }

    @Test
    void duplicateReferenceIsRejectedByTheDatabase() {
        String p = prefix();
        save(p + "DUP", "First", 5, 1, true);
        assertThrows(DataIntegrityViolationException.class, () -> save(p + "DUP", "Second", 5, 1, true));
    }

    @Test
    void newPartHasZeroLeadTimeAndVersionIsManaged() {
        Part part = save(prefix() + "V", "Versioned", 5, 1, true);
        assertEquals(0, part.getLeadTimeDays());
        long first = part.getVersion();
        part.setQuantityInStock(6);
        assertEquals(first + 1, partRepository.saveAndFlush(part).getVersion());
    }
}
