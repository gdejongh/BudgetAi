package com.budget.budgetai.dto;

import java.time.ZonedDateTime;

public class AiAdviceDTO {

    private String advice;
    private ZonedDateTime generatedAt;
    private ZonedDateTime cachedUntil;
    private int refreshesRemaining;

    public AiAdviceDTO() {
    }

    public AiAdviceDTO(String advice, ZonedDateTime generatedAt, ZonedDateTime cachedUntil, int refreshesRemaining) {
        this.advice = advice;
        this.generatedAt = generatedAt;
        this.cachedUntil = cachedUntil;
        this.refreshesRemaining = refreshesRemaining;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public ZonedDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(ZonedDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public ZonedDateTime getCachedUntil() {
        return cachedUntil;
    }

    public void setCachedUntil(ZonedDateTime cachedUntil) {
        this.cachedUntil = cachedUntil;
    }

    public int getRefreshesRemaining() {
        return refreshesRemaining;
    }

    public void setRefreshesRemaining(int refreshesRemaining) {
        this.refreshesRemaining = refreshesRemaining;
    }
}
