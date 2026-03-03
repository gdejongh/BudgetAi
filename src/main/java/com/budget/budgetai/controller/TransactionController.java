package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.CCPaymentRequest;
import com.budget.budgetai.dto.TransactionDTO;
import com.budget.budgetai.dto.TransferRequest;
import com.budget.budgetai.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(operationId = "createTransaction")
    public ResponseEntity<TransactionDTO> create(@Valid @RequestBody TransactionDTO transactionDTO) {
        UUID userId = SecurityUtils.getCurrentUserId();
        transactionDTO.setAppUserId(userId);
        TransactionDTO created = transactionService.create(transactionDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTransactionById")
    public ResponseEntity<TransactionDTO> getById(@PathVariable UUID id) {
        TransactionDTO transaction = transactionService.getById(id);
        SecurityUtils.verifyOwnership(transaction.getAppUserId());
        return ResponseEntity.ok(transaction);
    }

    @GetMapping
    @Operation(operationId = "getAllTransactions", summary = "Get all transactions for the current user")
    public ResponseEntity<List<TransactionDTO>> getByCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(transactionService.getByAppUserId(userId));
    }

    @GetMapping("/by-account/{bankAccountId}")
    @Operation(operationId = "getTransactionsByBankAccount")
    public ResponseEntity<List<TransactionDTO>> getByBankAccountId(@PathVariable UUID bankAccountId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<TransactionDTO> transactions = transactionService.getByBankAccountId(bankAccountId);
        transactions.forEach(t -> SecurityUtils.verifyOwnership(t.getAppUserId()));
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/by-envelope/{envelopeId}")
    @Operation(operationId = "getTransactionsByEnvelope")
    public ResponseEntity<List<TransactionDTO>> getByEnvelopeId(@PathVariable UUID envelopeId) {
        List<TransactionDTO> transactions = transactionService.getByEnvelopeId(envelopeId);
        transactions.forEach(t -> SecurityUtils.verifyOwnership(t.getAppUserId()));
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/by-date-range")
    @Operation(operationId = "getTransactionsByDateRange")
    public ResponseEntity<List<TransactionDTO>> getByDateRange(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity
                .ok(transactionService.getByAppUserIdAndTransactionDateBetween(userId, startDate, endDate));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTransaction")
    public ResponseEntity<TransactionDTO> update(@PathVariable UUID id,
            @Valid @RequestBody TransactionDTO transactionDTO) {
        TransactionDTO existing = transactionService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        UUID userId = SecurityUtils.getCurrentUserId();
        transactionDTO.setAppUserId(userId);
        return ResponseEntity.ok(transactionService.update(id, transactionDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteTransaction")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        TransactionDTO existing = transactionService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cc-payment")
    @Operation(operationId = "createCCPayment", summary = "Make a credit card payment from a bank account")
    public ResponseEntity<TransactionDTO> createCCPayment(@Valid @RequestBody CCPaymentRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        TransactionDTO created = transactionService.createCCPayment(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/transfer")
    @Operation(operationId = "createTransfer", summary = "Transfer money between two accounts")
    public ResponseEntity<List<TransactionDTO>> createTransfer(@Valid @RequestBody TransferRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<TransactionDTO> created = transactionService.createTransfer(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
