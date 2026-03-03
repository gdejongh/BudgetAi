package com.budget.budgetai.service;

import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.dto.EnvelopeSpentSummaryDTO;
import com.budget.budgetai.dto.TransactionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialSummaryBuilderTest {

    @Mock
    private BankAccountService bankAccountService;
    @Mock
    private EnvelopeService envelopeService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private EnvelopeAllocationService envelopeAllocationService;

    @InjectMocks
    private FinancialSummaryBuilder financialSummaryBuilder;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void buildSummary_noData_returnsEmptySections() {
        when(bankAccountService.getByAppUserId(userId)).thenReturn(Collections.emptyList());
        when(envelopeService.getByAppUserId(userId)).thenReturn(Collections.emptyList());
        when(transactionService.getByAppUserIdAndTransactionDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());

        String summary = financialSummaryBuilder.buildSummary(userId);

        assertNotNull(summary);
        assertTrue(summary.contains("ACCOUNTS: None"));
        assertTrue(summary.contains("ENVELOPES: None"));
        assertTrue(summary.contains("RECENT SPENDING (30 days): None"));
    }

    @Test
    void buildSummary_withAccounts_includesAccountDetails() {
        BankAccountDTO checking = new BankAccountDTO(UUID.randomUUID(), userId, "Main Checking",
                "CHECKING", new BigDecimal("2500.00"), null);
        BankAccountDTO savings = new BankAccountDTO(UUID.randomUUID(), userId, "Savings",
                "SAVINGS", new BigDecimal("10000.00"), null);
        when(bankAccountService.getByAppUserId(userId)).thenReturn(List.of(checking, savings));
        when(envelopeService.getByAppUserId(userId)).thenReturn(Collections.emptyList());
        when(transactionService.getByAppUserIdAndTransactionDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());

        String summary = financialSummaryBuilder.buildSummary(userId);

        assertTrue(summary.contains("Main Checking"));
        assertTrue(summary.contains("CHECKING"));
        assertTrue(summary.contains("$2500.00"));
        assertTrue(summary.contains("Savings"));
        assertTrue(summary.contains("$10000.00"));
        assertTrue(summary.contains("Total: $12500.00"));
    }

    @Test
    void buildSummary_withEnvelopes_includesEnvelopeDetails() {
        when(bankAccountService.getByAppUserId(userId)).thenReturn(Collections.emptyList());

        UUID envelopeId = UUID.randomUUID();
        EnvelopeDTO groceries = new EnvelopeDTO(envelopeId, userId, UUID.randomUUID(),
                "Groceries", new BigDecimal("500.00"), null);
        when(envelopeService.getByAppUserId(userId)).thenReturn(List.of(groceries));
        when(envelopeService.getSpentSummaries(eq(userId), any(), any()))
                .thenReturn(List.of(new EnvelopeSpentSummaryDTO(envelopeId,
                        new BigDecimal("-300.00"), new BigDecimal("-150.00"))));
        when(transactionService.getByAppUserIdAndTransactionDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());

        String summary = financialSummaryBuilder.buildSummary(userId);

        assertTrue(summary.contains("Groceries"));
        assertTrue(summary.contains("allocated $500.00"));
        assertTrue(summary.contains("spent this month $150.00"));
    }

    @Test
    void buildSummary_withTransactions_aggregatesByCategory() {
        when(bankAccountService.getByAppUserId(userId)).thenReturn(Collections.emptyList());
        when(envelopeService.getByAppUserId(userId)).thenReturn(Collections.emptyList());

        TransactionDTO txn1 = new TransactionDTO(UUID.randomUUID(), userId, UUID.randomUUID(),
                null, new BigDecimal("-50.00"), "Walmart", LocalDate.now(), "STANDARD", null, null);
        txn1.setMerchantName("Walmart");
        TransactionDTO txn2 = new TransactionDTO(UUID.randomUUID(), userId, UUID.randomUUID(),
                null, new BigDecimal("-30.00"), "Walmart", LocalDate.now(), "STANDARD", null, null);
        txn2.setMerchantName("Walmart");
        TransactionDTO txn3 = new TransactionDTO(UUID.randomUUID(), userId, UUID.randomUUID(),
                null, new BigDecimal("-100.00"), "Electric bill", LocalDate.now(), "STANDARD", null, null);
        txn3.setPlaidCategory("Utilities");

        when(transactionService.getByAppUserIdAndTransactionDateBetween(eq(userId), any(), any()))
                .thenReturn(List.of(txn1, txn2, txn3));

        String summary = financialSummaryBuilder.buildSummary(userId);

        assertTrue(summary.contains("Walmart"));
        assertTrue(summary.contains("$80.00"));
        assertTrue(summary.contains("2 txns"));
        assertTrue(summary.contains("Utilities"));
        assertTrue(summary.contains("$100.00"));
    }

    @Test
    void buildSummary_withEnvelopeGoals_includesGoalInfo() {
        when(bankAccountService.getByAppUserId(userId)).thenReturn(Collections.emptyList());

        EnvelopeDTO vacationEnvelope = new EnvelopeDTO(UUID.randomUUID(), userId, UUID.randomUUID(),
                "Vacation Fund", new BigDecimal("1200.00"), "STANDARD", null,
                new BigDecimal("5000.00"), new BigDecimal("400.00"),
                LocalDate.of(2026, 12, 31), "SAVINGS", null);
        when(envelopeService.getByAppUserId(userId)).thenReturn(List.of(vacationEnvelope));
        when(envelopeService.getSpentSummaries(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(transactionService.getByAppUserIdAndTransactionDateBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());

        String summary = financialSummaryBuilder.buildSummary(userId);

        assertTrue(summary.contains("Vacation Fund"));
        assertTrue(summary.contains("goal $5000.00"));
        assertTrue(summary.contains("2026-12-31"));
    }

    @Test
    void buildSummary_skipsPositiveTransactions() {
        when(bankAccountService.getByAppUserId(userId)).thenReturn(Collections.emptyList());
        when(envelopeService.getByAppUserId(userId)).thenReturn(Collections.emptyList());

        TransactionDTO income = new TransactionDTO(UUID.randomUUID(), userId, UUID.randomUUID(),
                null, new BigDecimal("2000.00"), "Paycheck", LocalDate.now(), "STANDARD", null, null);
        income.setMerchantName("Employer");

        when(transactionService.getByAppUserIdAndTransactionDateBetween(eq(userId), any(), any()))
                .thenReturn(List.of(income));

        String summary = financialSummaryBuilder.buildSummary(userId);

        assertTrue(summary.contains("No expenses"));
    }
}
