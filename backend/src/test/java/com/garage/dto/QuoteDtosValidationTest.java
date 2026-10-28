package com.garage.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.dto.QuoteDtos.QuoteLineRequest;
import com.garage.dto.QuoteDtos.QuoteRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bean Validation rules of the quote contract (the controller applies them with @Valid). */
class QuoteDtosValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static QuoteLineRequest line(String description, String quantity, String unitPrice) {
        return new QuoteLineRequest(description, new BigDecimal(quantity), new BigDecimal(unitPrice));
    }

    @Test
    void validQuotePasses() {
        assertTrue(validator.validate(new QuoteRequest(List.of(line("Oil", "1.25", "9.99")))).isEmpty());
    }

    @Test
    void atLeastOneLineIsRequired() {
        assertEquals(1, validator.validate(new QuoteRequest(List.of())).size());
    }

    @Test
    void atMostFiftyLines() {
        assertTrue(validator.validate(new QuoteRequest(Collections.nCopies(50, line("X", "1", "1")))).isEmpty());
        assertEquals(1, validator.validate(new QuoteRequest(Collections.nCopies(51, line("X", "1", "1")))).size());
    }

    @Test
    void descriptionMustBeNonBlankAndAtMost255Characters() {
        assertEquals(1, validator.validate(line(" ", "1", "1")).size());
        assertTrue(validator.validate(line("a".repeat(255), "1", "1")).isEmpty());
        assertEquals(1, validator.validate(line("a".repeat(256), "1", "1")).size());
    }

    @Test
    void quantityMustBePositiveWithAtMostTwoDecimals() {
        assertEquals(1, validator.validate(line("X", "0", "1")).size());
        assertEquals(1, validator.validate(line("X", "-1", "1")).size());
        assertEquals(1, validator.validate(line("X", "1.001", "1")).size());
        assertTrue(validator.validate(line("X", "0.01", "1")).isEmpty());
    }

    @Test
    void unitPriceMustNotBeNegative() {
        assertTrue(validator.validate(line("X", "1", "0")).isEmpty());
        assertEquals(1, validator.validate(line("X", "1", "-0.01")).size());
    }

    @Test
    void nestedLinesAreValidated() {
        assertEquals(1, validator.validate(new QuoteRequest(List.of(line("", "1", "1")))).size());
    }
}
