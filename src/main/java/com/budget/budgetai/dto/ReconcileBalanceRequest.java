package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ReconcileBalanceRequest {

    @NotNull(message = "Target balance is required")
    private BigDecimal targetBalance;

    public ReconcileBalanceRequest() {
    }

    public ReconcileBalanceRequest(BigDecimal targetBalance) {
        this.targetBalance = targetBalance;
    }

    public BigDecimal getTargetBalance() {
        return targetBalance;
    }

    public void setTargetBalance(BigDecimal targetBalance) {
        this.targetBalance = targetBalance;
    }
}
