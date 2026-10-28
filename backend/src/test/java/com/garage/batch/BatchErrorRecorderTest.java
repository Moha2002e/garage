package com.garage.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BatchErrorRecorderTest {

    @Test
    void messagesAreNeverEmptyNorLongerThanTheColumn() {
        assertEquals("No detail", BatchErrorRecorder.truncate(null));
        assertEquals("No detail", BatchErrorRecorder.truncate("  "));
        assertEquals("short", BatchErrorRecorder.truncate("short"));
        assertEquals(500, BatchErrorRecorder.truncate("x".repeat(900)).length());
    }
}
