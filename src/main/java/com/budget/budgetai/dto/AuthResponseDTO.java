package com.budget.budgetai.dto;

import java.util.UUID;

public class AuthResponseDTO {

    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private String email;

    public AuthResponseDTO() {
    }

    public AuthResponseDTO(String accessToken, String refreshToken, UUID userId, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
