package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.TransactionDTO;
import com.budget.budgetai.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionDTO> create(@Valid @RequestBody TransactionDTO transactionDTO) {
        UUID userId = SecurityUtils.getCurrentUserId();
        transactionDTO.setAppUserId(userId);
        TransactionDTO created = transactionService.create(transactionDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getById(@PathVariable UUID id) {
        TransactionDTO transaction = transactionService.getById(id);
        SecurityUtils.verifyOwnership(transaction.getAppUserId());
        return ResponseEntity.ok(transaction);
    }

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getByCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(transactionService.getByAppUserId(userId));
    }

    @GetMapping(params = "bankAccountId")
    public ResponseEntity<List<TransactionDTO>> getByBankAccountId(@RequestParam UUID bankAccountId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        // Verify the bank account belongs to the user by checking returned transactions
        List<TransactionDTO> transactions = transactionService.getByBankAccountId(bankAccountId);
        transactions.forEach(t -> SecurityUtils.verifyOwnership(t.getAppUserId()));
        return ResponseEntity.ok(transactions);
    }

    @GetMapping(params = "envelopeId")
    public ResponseEntity<List<TransactionDTO>> getByEnvelopeId(@RequestParam UUID envelopeId) {
        List<TransactionDTO> transactions = transactionService.getByEnvelopeId(envelopeId);
        transactions.forEach(t -> SecurityUtils.verifyOwnership(t.getAppUserId()));
        return ResponseEntity.ok(transactions);
    }

    @GetMapping(params = { "startDate", "endDate" })
    public ResponseEntity<List<TransactionDTO>> getByDateRange(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity
                .ok(transactionService.getByAppUserIdAndTransactionDateBetween(userId, startDate, endDate));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionDTO> update(@PathVariable UUID id,
            @Valid @RequestBody TransactionDTO transactionDTO) {
        TransactionDTO existing = transactionService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        UUID userId = SecurityUtils.getCurrentUserId();
        transactionDTO.setAppUserId(userId);
        return ResponseEntity.ok(transactionService.update(id, transactionDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        TransactionDTO existing = transactionService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
