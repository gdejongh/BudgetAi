package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class PlaidAccountLink {

    @NotBlank(message = "Plaid account ID is required")
    private String plaidAccountId;

    private UUID existingBankAccountId;

    private String accountName;

    private String accountType;

    private String mask;

    public PlaidAccountLink() {
    }

    public PlaidAccountLink(String plaidAccountId, UUID existingBankAccountId, String accountName,
            String accountType, String mask) {
        this.plaidAccountId = plaidAccountId;
        this.existingBankAccountId = existingBankAccountId;
        this.accountName = accountName;
        this.accountType = accountType;
        this.mask = mask;
    }

    public String getPlaidAccountId() {
        return plaidAccountId;
    }

    public void setPlaidAccountId(String plaidAccountId) {
        this.plaidAccountId = plaidAccountId;
    }

    public UUID getExistingBankAccountId() {
        return existingBankAccountId;
    }

    public void setExistingBankAccountId(UUID existingBankAccountId) {
        this.existingBankAccountId = existingBankAccountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }
}
