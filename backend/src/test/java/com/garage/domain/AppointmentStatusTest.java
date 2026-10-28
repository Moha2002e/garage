package com.garage.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppointmentStatusTest {

    @Test
    void plannedCanStartCompleteOrCancel() {
        assertTrue(AppointmentStatus.PLANNED.canTransitionTo(AppointmentStatus.IN_PROGRESS));
        assertTrue(AppointmentStatus.PLANNED.canTransitionTo(AppointmentStatus.COMPLETED));
        assertTrue(AppointmentStatus.PLANNED.canTransitionTo(AppointmentStatus.CANCELLED));
        assertFalse(AppointmentStatus.PLANNED.canTransitionTo(AppointmentStatus.PLANNED));
    }

    @Test
    void inProgressCanOnlyCompleteOrCancel() {
        assertTrue(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.COMPLETED));
        assertTrue(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.CANCELLED));
        assertFalse(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.PLANNED));
        assertFalse(AppointmentStatus.IN_PROGRESS.canTransitionTo(AppointmentStatus.IN_PROGRESS));
    }

    @Test
    void finalStatusesNeverChange() {
        for (AppointmentStatus target : AppointmentStatus.values()) {
            assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(target));
            assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(target));
        }
    }

    @Test
    void onlyPlannedAndInProgressOccupyTheAgenda() {
        assertTrue(AppointmentStatus.PLANNED.occupiesAgenda());
        assertTrue(AppointmentStatus.IN_PROGRESS.occupiesAgenda());
        assertFalse(AppointmentStatus.COMPLETED.occupiesAgenda());
        assertFalse(AppointmentStatus.CANCELLED.occupiesAgenda());
    }
}
