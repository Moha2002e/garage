package com.garage.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoiceNumberGeneratorTest {

    @Test
    void numberIsFacYearAndSixDigitSequence() {
        assertEquals("FAC-2026-000001", InvoiceNumberGenerator.format(2026, 1));
        assertEquals("FAC-2026-000042", InvoiceNumberGenerator.format(2026, 42));
        assertEquals("FAC-2031-1234567", InvoiceNumberGenerator.format(2031, 1234567));
    }

    @Test
    void yearIsTheYearOfIssueInParisAndTheSequenceComesFromTheDatabase() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(7L);

        // 23:30 UTC on December 31st is already January 1st in Paris.
        String number = new InvoiceNumberGenerator(entityManager).next(Instant.parse("2026-12-31T23:30:00Z"));

        assertEquals("FAC-2027-000007", number);
    }
}
