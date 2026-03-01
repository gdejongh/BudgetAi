package com.budget.budgetai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "envelope_allocation", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "envelope_id", "year_month" })
})
@Getter
@Setter
public class EnvelopeAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envelope_id", nullable = false)
    private Envelope envelope;

    @Column(name = "year_month", nullable = false)
    private LocalDate yearMonth;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
}
