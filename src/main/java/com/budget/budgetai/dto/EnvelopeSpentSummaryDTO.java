package com.budget.budgetai.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class EnvelopeSpentSummaryDTO {

    private UUID envelopeId;
    private BigDecimal totalSpent;
    private BigDecimal periodSpent;

    public EnvelopeSpentSummaryDTO() {
    }

    public EnvelopeSpentSummaryDTO(UUID envelopeId, BigDecimal totalSpent, BigDecimal periodSpent) {
        this.envelopeId = envelopeId;
        this.totalSpent = totalSpent;
        this.periodSpent = periodSpent;
    }

    public UUID getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(UUID envelopeId) {
        this.envelopeId = envelopeId;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    public BigDecimal getPeriodSpent() {
        return periodSpent;
    }

    public void setPeriodSpent(BigDecimal periodSpent) {
        this.periodSpent = periodSpent;
    }
}
