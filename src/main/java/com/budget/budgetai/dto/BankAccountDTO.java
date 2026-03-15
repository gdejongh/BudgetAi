package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public class BankAccountDTO {

    private UUID id;

    @NotNull(message = "App user ID is required")
    private UUID appUserId;

    @NotBlank(message = "Name is required")
    private String name;

    private String accountType;

    @NotNull(message = "Current balance is required")
    private BigDecimal currentBalance;

    private ZonedDateTime createdAt;

    private String plaidAccountId;

    private UUID plaidItemId;

    private String accountMask;

    private boolean manual = true;

    private String institutionName;

    private ZonedDateTime plaidLinkedAt;

    private Integer displayOrder;

    public BankAccountDTO() {
    }

    public BankAccountDTO(UUID id, UUID appUserId, String name, String accountType, BigDecimal currentBalance,
            ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.name = name;
        this.accountType = accountType;
        this.currentBalance = currentBalance;
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

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPlaidAccountId() {
        return plaidAccountId;
    }

    public void setPlaidAccountId(String plaidAccountId) {
        this.plaidAccountId = plaidAccountId;
    }

    public UUID getPlaidItemId() {
        return plaidItemId;
    }

    public void setPlaidItemId(UUID plaidItemId) {
        this.plaidItemId = plaidItemId;
    }

    public String getAccountMask() {
        return accountMask;
    }

    public void setAccountMask(String accountMask) {
        this.accountMask = accountMask;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public ZonedDateTime getPlaidLinkedAt() {
        return plaidLinkedAt;
    }

    public void setPlaidLinkedAt(ZonedDateTime plaidLinkedAt) {
        this.plaidLinkedAt = plaidLinkedAt;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
