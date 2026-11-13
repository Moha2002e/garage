package com.garage.domain;

public enum StockMovementType {
    /** Stock received: adds the quantity. */
    IN,
    /** Stock used (repair, sale): removes the quantity. */
    OUT,
    /** Inventory correction: the quantity is the signed difference to apply. */
    ADJUSTMENT
}
