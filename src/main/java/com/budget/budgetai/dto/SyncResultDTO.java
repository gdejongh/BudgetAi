package com.budget.budgetai.dto;

public record SyncResultDTO(int itemsSynced, int itemsFailed, String message) {
}
