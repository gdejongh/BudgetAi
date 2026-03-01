package com.budget.budgetai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class EnvelopeAllocationDTO {

    private UUID envelopeId;
    private LocalDate yearMonth;
    private BigDecimal amount;

    public EnvelopeAllocationDTO() {
    }

    public EnvelopeAllocationDTO(UUID envelopeId, LocalDate yearMonth, BigDecimal amount) {
        this.envelopeId = envelopeId;
        this.yearMonth = yearMonth;
        this.amount = amount;
    }

    public UUID getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(UUID envelopeId) {
        this.envelopeId = envelopeId;
    }

    public LocalDate getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(LocalDate yearMonth) {
        this.yearMonth = yearMonth;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
