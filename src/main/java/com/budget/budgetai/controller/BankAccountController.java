package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.service.BankAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bank-accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    public ResponseEntity<BankAccountDTO> create(@Valid @RequestBody BankAccountDTO bankAccountDTO) {
        UUID userId = SecurityUtils.getCurrentUserId();
        bankAccountDTO.setAppUserId(userId);
        BankAccountDTO created = bankAccountService.create(bankAccountDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BankAccountDTO> getById(@PathVariable UUID id) {
        BankAccountDTO account = bankAccountService.getById(id);
        SecurityUtils.verifyOwnership(account.getAppUserId());
        return ResponseEntity.ok(account);
    }

    @GetMapping
    public ResponseEntity<List<BankAccountDTO>> getByCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(bankAccountService.getByAppUserId(userId));
    }

    @GetMapping(params = "name")
    public ResponseEntity<List<BankAccountDTO>> getByName(@RequestParam String name) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(bankAccountService.getByAppUserIdAndName(userId, name));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankAccountDTO> update(@PathVariable UUID id,
            @Valid @RequestBody BankAccountDTO bankAccountDTO) {
        BankAccountDTO existing = bankAccountService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        return ResponseEntity.ok(bankAccountService.update(id, bankAccountDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        BankAccountDTO existing = bankAccountService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        bankAccountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
