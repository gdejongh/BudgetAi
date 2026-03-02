package com.budget.budgetai.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class PlaidItemDTO {

    private UUID id;
    private String institutionId;
    private String institutionName;
    private String status;
    private ZonedDateTime lastSyncedAt;
    private ZonedDateTime createdAt;
    private List<BankAccountDTO> accounts;

    public PlaidItemDTO() {
    }

    public PlaidItemDTO(UUID id, String institutionId, String institutionName, String status,
            ZonedDateTime lastSyncedAt, ZonedDateTime createdAt, List<BankAccountDTO> accounts) {
        this.id = id;
        this.institutionId = institutionId;
        this.institutionName = institutionName;
        this.status = status;
        this.lastSyncedAt = lastSyncedAt;
        this.createdAt = createdAt;
        this.accounts = accounts;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ZonedDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(ZonedDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<BankAccountDTO> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<BankAccountDTO> accounts) {
        this.accounts = accounts;
    }
}
