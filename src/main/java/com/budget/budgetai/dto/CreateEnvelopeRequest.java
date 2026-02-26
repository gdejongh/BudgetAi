package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateEnvelopeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Allocated balance is required")
    private BigDecimal allocatedBalance;

    public CreateEnvelopeRequest() {
    }

    public CreateEnvelopeRequest(String name, BigDecimal allocatedBalance) {
        this.name = name;
        this.allocatedBalance = allocatedBalance;
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
}
