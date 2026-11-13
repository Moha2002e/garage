package com.garage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "parts")
@Getter
@Setter
public class Part extends Auditable {

    /** Unique catalogue code, stored trimmed and upper-case. */
    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(nullable = false)
    private String name;

    /** Unit price excluding VAT. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Only changed through a stock movement, never edited directly. */
    @Column(name = "quantity_in_stock", nullable = false)
    private int quantityInStock;

    /** Below or at this quantity the part is flagged as low on stock. */
    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    /** Supplier delivery time in days (0 = unknown), shown when the stock is too low for a quote. */
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    /** A part is never deleted: it is deactivated instead. */
    @Column(nullable = false)
    private boolean active = true;

    /** Computed, not stored: true when the stock is at or below the alert threshold. */
    public boolean isLowStock() {
        return quantityInStock <= lowStockThreshold;
    }

    /** Optimistic lock: detects two simultaneous stock updates. */
    @Version
    @Column(nullable = false)
    private long version;
}
