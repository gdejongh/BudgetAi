package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateEnvelopeCategoryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    public CreateEnvelopeCategoryRequest() {
    }

    public CreateEnvelopeCategoryRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
