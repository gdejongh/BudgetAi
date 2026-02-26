package com.budget.budgetai.controller;

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
        BankAccountDTO created = bankAccountService.create(bankAccountDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BankAccountDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(bankAccountService.getById(id));
    }

    @GetMapping(params = "userId")
    public ResponseEntity<List<BankAccountDTO>> getByAppUserId(@RequestParam UUID userId) {
        return ResponseEntity.ok(bankAccountService.getByAppUserId(userId));
    }

    @GetMapping(params = {"userId", "name"})
    public ResponseEntity<List<BankAccountDTO>> getByAppUserIdAndName(@RequestParam UUID userId, @RequestParam String name) {
        return ResponseEntity.ok(bankAccountService.getByAppUserIdAndName(userId, name));
    }

    @GetMapping
    public ResponseEntity<List<BankAccountDTO>> getAll() {
        return ResponseEntity.ok(bankAccountService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankAccountDTO> update(@PathVariable UUID id, @Valid @RequestBody BankAccountDTO bankAccountDTO) {
        return ResponseEntity.ok(bankAccountService.update(id, bankAccountDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bankAccountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
