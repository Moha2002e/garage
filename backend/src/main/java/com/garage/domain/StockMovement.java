package com.garage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One line of the stock journal. Never updated or deleted once written. */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
public class StockMovement extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id")
    private Part part;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMovementType type;

    /** Strictly positive for IN and OUT; signed for ADJUSTMENT. */
    @Column(nullable = false)
    private int quantity;

    /** Free text or business reference (for example the appointment id). */
    private String reason;
}
