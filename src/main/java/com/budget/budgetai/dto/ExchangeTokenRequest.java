package com.budget.budgetai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ExchangeTokenRequest {

    @NotBlank(message = "Public token is required")
    private String publicToken;

    private String institutionId;

    private String institutionName;

    @NotEmpty(message = "Account links are required")
    @Valid
    private List<PlaidAccountLink> accountLinks;

    public ExchangeTokenRequest() {
    }

    public ExchangeTokenRequest(String publicToken, String institutionId, String institutionName,
            List<PlaidAccountLink> accountLinks) {
        this.publicToken = publicToken;
        this.institutionId = institutionId;
        this.institutionName = institutionName;
        this.accountLinks = accountLinks;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String publicToken) {
        this.publicToken = publicToken;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public List<PlaidAccountLink> getAccountLinks() {
        return accountLinks;
    }

    public void setAccountLinks(List<PlaidAccountLink> accountLinks) {
        this.accountLinks = accountLinks;
    }
}
