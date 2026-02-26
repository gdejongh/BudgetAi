package com.budget.budgetai.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshRequestDTO {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    public RefreshRequestDTO() {
    }

    public RefreshRequestDTO(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
