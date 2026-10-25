package com.garage.domain;

public enum AppointmentStatus {
    PLANNED,
    COMPLETED,
    CANCELLED;

    /** A planned appointment can be completed or cancelled; final statuses never change. */
    public boolean canTransitionTo(AppointmentStatus target) {
        return this == PLANNED && (target == COMPLETED || target == CANCELLED);
    }
}
