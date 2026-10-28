package com.garage.domain;

public enum AppointmentStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /**
     * PLANNED can start, complete or cancel; IN_PROGRESS can complete or cancel.
     * COMPLETED and CANCELLED are final and never change.
     */
    public boolean canTransitionTo(AppointmentStatus target) {
        return switch (this) {
            case PLANNED -> target == IN_PROGRESS || target == COMPLETED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** Statuses that occupy the garage agenda (used by the schedule conflict rules). */
    public boolean occupiesAgenda() {
        return this == PLANNED || this == IN_PROGRESS;
    }
}
