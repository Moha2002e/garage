package com.garage.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppointmentStatusTest {

    @Test
    void plannedCanBeCompletedOrCancelled() {
        assertTrue(AppointmentStatus.PLANNED.canTransitionTo(AppointmentStatus.COMPLETED));
        assertTrue(AppointmentStatus.PLANNED.canTransitionTo(AppointmentStatus.CANCELLED));
        assertFalse(AppointmentStatus.PLANNED.canTransitionTo(AppointmentStatus.PLANNED));
    }

    @Test
    void finalStatusesNeverChange() {
        for (AppointmentStatus target : AppointmentStatus.values()) {
            assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(target));
            assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(target));
        }
    }
}
