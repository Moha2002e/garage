package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExportServiceTest {

    @Test
    void prefixesFormulaCharacters() {
        assertEquals("'=1+1", ExportService.safe("=1+1"));
        assertEquals("'+33612345678", ExportService.safe("+33612345678"));
        assertEquals("'-2", ExportService.safe("-2"));
        assertEquals("'@cmd", ExportService.safe("@cmd"));
    }

    @Test
    void keepsNormalValuesAndNulls() {
        assertEquals("Dupont", ExportService.safe("Dupont"));
        assertEquals("", ExportService.safe(null));
    }
}
