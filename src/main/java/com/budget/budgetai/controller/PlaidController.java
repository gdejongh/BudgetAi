package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.*;
import com.budget.budgetai.service.PlaidService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/plaid", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlaidController {

    private final PlaidService plaidService;

    public PlaidController(PlaidService plaidService) {
        this.plaidService = plaidService;
    }

    @PostMapping("/link-token")
    @Operation(operationId = "createLinkToken", summary = "Create a Plaid Link token for the current user")
    public ResponseEntity<LinkTokenResponse> createLinkToken() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String linkToken = plaidService.createLinkToken(userId);
        return ResponseEntity.ok(new LinkTokenResponse(linkToken));
    }

    @PostMapping("/exchange-token")
    @Operation(operationId = "exchangePublicToken", summary = "Exchange a Plaid public token and link accounts")
    public ResponseEntity<List<BankAccountDTO>> exchangePublicToken(
            @Valid @RequestBody ExchangeTokenRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<BankAccountDTO> accounts = plaidService.exchangePublicToken(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts);
    }

    @GetMapping("/items")
    @Operation(operationId = "getPlaidItems", summary = "Get all Plaid connections for the current user")
    public ResponseEntity<List<PlaidItemDTO>> getPlaidItems() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(plaidService.getItemsByUserId(userId));
    }

    @DeleteMapping("/items/{id}")
    @Operation(operationId = "unlinkPlaidItem", summary = "Disconnect a Plaid connection")
    public ResponseEntity<Void> unlinkPlaidItem(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        plaidService.unlinkItem(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    @Operation(operationId = "syncAccounts", summary = "Sync transactions and balances for all linked Plaid accounts")
    public ResponseEntity<SyncResultDTO> syncAccounts() {
        UUID userId = SecurityUtils.getCurrentUserId();
        SyncResultDTO result = plaidService.syncAllItems(userId);
        return ResponseEntity.ok(result);
    }

}
