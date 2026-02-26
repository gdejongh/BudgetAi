package com.budget.budgetai.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public class BankAccountDTO {

    private UUID id;
    private UUID appUserId;
    private String name;
    private BigDecimal currentBalance;
    private ZonedDateTime createdAt;

    public BankAccountDTO() {
    }

    public BankAccountDTO(UUID id, UUID appUserId, String name, BigDecimal currentBalance, ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.name = name;
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
}
