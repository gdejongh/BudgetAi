package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

public class EnvelopeCategoryDTO {

    private UUID id;

    @NotNull(message = "App user ID is required")
    private UUID appUserId;

    @NotBlank(message = "Name is required")
    private String name;

    private String categoryType;

    private ZonedDateTime createdAt;

    private Integer displayOrder;

    public EnvelopeCategoryDTO() {
    }

    public EnvelopeCategoryDTO(UUID id, UUID appUserId, String name, ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public EnvelopeCategoryDTO(UUID id, UUID appUserId, String name, String categoryType, ZonedDateTime createdAt) {
        this.id = id;
        this.appUserId = appUserId;
        this.name = name;
        this.categoryType = categoryType;
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

    public String getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(String categoryType) {
        this.categoryType = categoryType;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
