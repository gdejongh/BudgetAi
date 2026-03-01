package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

public class TransactionDTO {

    private UUID id;

    private UUID appUserId;

    @NotNull(message = "Bank account ID is required")
    private UUID bankAccountId;

    private UUID envelopeId;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    private String description;

    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    private String transactionType;

    private UUID linkedTransactionId;

    private ZonedDateTime createdAt;

    public TransactionDTO() {
    }

    public TransactionDTO(UUID id, UUID appUserId, UUID bankAccountId, UUID envelopeId, BigDecimal amount,
            String description, LocalDate transactionDate, String transactionType, UUID linkedTransactionId,
            ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.bankAccountId = bankAccountId;
        this.envelopeId = envelopeId;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.linkedTransactionId = linkedTransactionId;
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

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(UUID bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public UUID getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(UUID envelopeId) {
        this.envelopeId = envelopeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public UUID getLinkedTransactionId() {
        return linkedTransactionId;
    }

    public void setLinkedTransactionId(UUID linkedTransactionId) {
        this.linkedTransactionId = linkedTransactionId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
