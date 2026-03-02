package com.budget.budgetai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "envelope")
@Getter
@Setter
public class Envelope {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envelope_category_id", nullable = false)
    private EnvelopeCategory envelopeCategory;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "allocated_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "envelope_type", nullable = false, length = 20)
    private EnvelopeType envelopeType = EnvelopeType.STANDARD;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_account_id")
    private BankAccount linkedAccount;

    @Column(name = "goal_amount", precision = 19, scale = 2)
    private BigDecimal goalAmount;

    @Column(name = "monthly_goal_target", precision = 19, scale = 2)
    private BigDecimal monthlyGoalTarget;

    @Column(name = "goal_target_date")
    private LocalDate goalTargetDate;

    @Column(name = "goal_type", length = 20)
    private String goalType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}