package com.garage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PlateUtilTest {

    @Test
    void normalizesValidPlates() {
        assertEquals("AB-123-CD", PlateUtil.normalize("ab123cd"));
        assertEquals("AB-123-CD", PlateUtil.normalize(" ab 123 cd "));
        assertEquals("AB-123-CD", PlateUtil.normalize("AB-123-CD"));
    }

    @Test
    void rejectsInvalidPlates() {
        assertNull(PlateUtil.normalize("1234 AB 56"));
        assertNull(PlateUtil.normalize("A-123-BC"));
        assertNull(PlateUtil.normalize("ABC-123-DE"));
        assertNull(PlateUtil.normalize(null));
    }
}
