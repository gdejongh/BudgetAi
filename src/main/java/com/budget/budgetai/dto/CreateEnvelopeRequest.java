package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateEnvelopeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Allocated balance is required")
    private BigDecimal allocatedBalance;

    @NotNull(message = "Envelope category ID is required")
    private UUID envelopeCategoryId;

    public CreateEnvelopeRequest() {
    }

    public CreateEnvelopeRequest(String name, BigDecimal allocatedBalance, UUID envelopeCategoryId) {
        this.name = name;
        this.allocatedBalance = allocatedBalance;
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

    public UUID getEnvelopeCategoryId() {
        return envelopeCategoryId;
    }

    public void setEnvelopeCategoryId(UUID envelopeCategoryId) {
        this.envelopeCategoryId = envelopeCategoryId;
    }
}
