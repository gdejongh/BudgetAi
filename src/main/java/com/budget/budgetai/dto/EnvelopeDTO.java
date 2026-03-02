package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public class EnvelopeDTO {

    private UUID id;

    @NotNull(message = "App user ID is required")
    private UUID appUserId;

    @NotNull(message = "Envelope category ID is required")
    private UUID envelopeCategoryId;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Allocated balance is required")
    private BigDecimal allocatedBalance;

    private String envelopeType;

    private UUID linkedAccountId;

    private ZonedDateTime createdAt;

    public EnvelopeDTO() {
    }

    public EnvelopeDTO(UUID id, UUID appUserId, UUID envelopeCategoryId, String name, BigDecimal allocatedBalance,
            ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.envelopeCategoryId = envelopeCategoryId;
        this.name = name;
        this.allocatedBalance = allocatedBalance;
        this.createdAt = createdAt;
    }

    public EnvelopeDTO(UUID id, UUID appUserId, UUID envelopeCategoryId, String name, BigDecimal allocatedBalance,
            String envelopeType, UUID linkedAccountId, ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.envelopeCategoryId = envelopeCategoryId;
        this.name = name;
        this.allocatedBalance = allocatedBalance;
        this.envelopeType = envelopeType;
        this.linkedAccountId = linkedAccountId;
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

    public UUID getEnvelopeCategoryId() {
        return envelopeCategoryId;
    }

    public void setEnvelopeCategoryId(UUID envelopeCategoryId) {
        this.envelopeCategoryId = envelopeCategoryId;
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

    public String getEnvelopeType() {
        return envelopeType;
    }

    public void setEnvelopeType(String envelopeType) {
        this.envelopeType = envelopeType;
    }

    public UUID getLinkedAccountId() {
        return linkedAccountId;
    }

    public void setLinkedAccountId(UUID linkedAccountId) {
        this.linkedAccountId = linkedAccountId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
