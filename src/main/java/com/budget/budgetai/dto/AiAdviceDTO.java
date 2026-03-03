package com.budget.budgetai.dto;

import java.time.ZonedDateTime;

public class AiAdviceDTO {

    private String advice;
    private ZonedDateTime generatedAt;
    private ZonedDateTime cachedUntil;

    public AiAdviceDTO() {
    }

    public AiAdviceDTO(String advice, ZonedDateTime generatedAt, ZonedDateTime cachedUntil) {
        this.advice = advice;
        this.generatedAt = generatedAt;
        this.cachedUntil = cachedUntil;
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
}
