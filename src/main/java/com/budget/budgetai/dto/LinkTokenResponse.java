package com.budget.budgetai.dto;

public class LinkTokenResponse {

    private String linkToken;

    public LinkTokenResponse() {
    }

    public LinkTokenResponse(String linkToken) {
        this.linkToken = linkToken;
    }

    public String getLinkToken() {
        return linkToken;
    }

    public void setLinkToken(String linkToken) {
        this.linkToken = linkToken;
    }
}
