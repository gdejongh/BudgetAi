package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SetAllocationRequest {

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    public SetAllocationRequest() {
    }

    public SetAllocationRequest(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
