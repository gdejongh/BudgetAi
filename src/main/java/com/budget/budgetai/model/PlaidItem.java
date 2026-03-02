package com.budget.budgetai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "plaid_item")
@Getter
@Setter
public class PlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(name = "item_id", nullable = false, unique = true)
    private String itemId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "institution_id")
    private String institutionId;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "transaction_cursor", columnDefinition = "TEXT")
    private String transactionCursor;

    @Column(name = "last_synced_at")
    private ZonedDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaidItemStatus status = PlaidItemStatus.ACTIVE;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
