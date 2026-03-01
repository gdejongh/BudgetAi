package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateBankAccountRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String accountType;

    @NotNull(message = "Current balance is required")
    private BigDecimal currentBalance;

    public CreateBankAccountRequest() {
    }

    public CreateBankAccountRequest(String name, String accountType, BigDecimal currentBalance) {
        this.name = name;
        this.accountType = accountType;
        this.currentBalance = currentBalance;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }
}
