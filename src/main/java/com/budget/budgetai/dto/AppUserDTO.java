package com.budget.budgetai.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public class AppUserDTO {

    private UUID id;
    private String email;
    private ZonedDateTime createdAt;

    public AppUserDTO() {
    }

    public AppUserDTO(UUID id, String email, ZonedDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
