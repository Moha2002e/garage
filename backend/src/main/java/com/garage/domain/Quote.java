package com.garage.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** The quote of an appointment (at most one per appointment). The total is computed by the back-end. */
@Entity
@Table(name = "quotes")
@Getter
@Setter
public class Quote extends Auditable {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Replaced lines stay in the table with active = false (no physical deletion). */
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL)
    @OrderBy("sortOrder ASC")
    private List<QuoteLine> lines = new ArrayList<>();
}
