package com.budget.budgetai.controller;

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
        TransactionDTO created = transactionService.create(transactionDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @GetMapping(params = "userId")
    public ResponseEntity<List<TransactionDTO>> getByAppUserId(@RequestParam UUID userId) {
        return ResponseEntity.ok(transactionService.getByAppUserId(userId));
    }

    @GetMapping(params = "bankAccountId")
    public ResponseEntity<List<TransactionDTO>> getByBankAccountId(@RequestParam UUID bankAccountId) {
        return ResponseEntity.ok(transactionService.getByBankAccountId(bankAccountId));
    }

    @GetMapping(params = "envelopeId")
    public ResponseEntity<List<TransactionDTO>> getByEnvelopeId(@RequestParam UUID envelopeId) {
        return ResponseEntity.ok(transactionService.getByEnvelopeId(envelopeId));
    }

    @GetMapping(params = {"startDate", "endDate"})
    public ResponseEntity<List<TransactionDTO>> getByDateRange(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getByTransactionDateBetween(startDate, endDate));
    }

    @GetMapping(params = {"userId", "startDate", "endDate"})
    public ResponseEntity<List<TransactionDTO>> getByAppUserIdAndDateRange(
            @RequestParam UUID userId, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(transactionService.getByAppUserIdAndTransactionDateBetween(userId, startDate, endDate));
    }

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getAll() {
        return ResponseEntity.ok(transactionService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionDTO> update(@PathVariable UUID id, @Valid @RequestBody TransactionDTO transactionDTO) {
        return ResponseEntity.ok(transactionService.update(id, transactionDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
