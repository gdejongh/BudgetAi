package com.budget.budgetai.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public class EnvelopeDTO {

    private UUID id;
    private UUID appUserId;
    private String name;
    private BigDecimal allocatedBalance;
    private ZonedDateTime createdAt;

    public EnvelopeDTO() {
    }

    public EnvelopeDTO(UUID id, UUID appUserId, String name, BigDecimal allocatedBalance, ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.name = name;
        this.allocatedBalance = allocatedBalance;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAppUserId() {
        return appUserId;
    }

    public void setAppUserId(UUID appUserId) {
        this.appUserId = appUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAllocatedBalance() {
        return allocatedBalance;
    }

    public void setAllocatedBalance(BigDecimal allocatedBalance) {
        this.allocatedBalance = allocatedBalance;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
