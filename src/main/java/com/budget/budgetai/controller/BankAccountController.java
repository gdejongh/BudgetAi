package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.dto.CreateBankAccountRequest;
import com.budget.budgetai.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/bank-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    @Operation(operationId = "createBankAccount")
    public ResponseEntity<BankAccountDTO> create(@Valid @RequestBody CreateBankAccountRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        BankAccountDTO bankAccountDTO = new BankAccountDTO();
        bankAccountDTO.setAppUserId(userId);
        bankAccountDTO.setName(request.getName());
        bankAccountDTO.setAccountType(request.getAccountType());
        bankAccountDTO.setCurrentBalance(request.getCurrentBalance());
        BankAccountDTO created = bankAccountService.create(bankAccountDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getBankAccountById")
    public ResponseEntity<BankAccountDTO> getById(@PathVariable UUID id) {
        BankAccountDTO account = bankAccountService.getById(id);
        SecurityUtils.verifyOwnership(account.getAppUserId());
        return ResponseEntity.ok(account);
    }

    @GetMapping
    @Operation(operationId = "getBankAccounts", summary = "Get bank accounts for current user, optionally filtered by name")
    public ResponseEntity<List<BankAccountDTO>> getBankAccounts(
            @Parameter(description = "Filter by account name") @RequestParam(required = false) String name) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (name != null) {
            return ResponseEntity.ok(bankAccountService.getByAppUserIdAndName(userId, name));
        }
        return ResponseEntity.ok(bankAccountService.getByAppUserId(userId));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateBankAccount")
    public ResponseEntity<BankAccountDTO> update(@PathVariable UUID id,
            @Valid @RequestBody BankAccountDTO bankAccountDTO) {
        BankAccountDTO existing = bankAccountService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        return ResponseEntity.ok(bankAccountService.update(id, bankAccountDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteBankAccount")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        BankAccountDTO existing = bankAccountService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        bankAccountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
