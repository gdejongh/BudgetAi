package com.budget.budgetai.service;

import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.dto.EnvelopeSpentSummaryDTO;
import com.budget.budgetai.dto.TransactionDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a compact text summary of a user's financial data for LLM consumption.
 * Aggregates raw data to minimize token usage while preserving actionable
 * context.
 */
@Service
public class FinancialSummaryBuilder {

    private final BankAccountService bankAccountService;
    private final EnvelopeService envelopeService;
    private final TransactionService transactionService;
    private final EnvelopeAllocationService envelopeAllocationService;

    public FinancialSummaryBuilder(BankAccountService bankAccountService,
            EnvelopeService envelopeService,
            TransactionService transactionService,
            EnvelopeAllocationService envelopeAllocationService) {
        this.bankAccountService = bankAccountService;
        this.envelopeService = envelopeService;
        this.transactionService = transactionService;
        this.envelopeAllocationService = envelopeAllocationService;
    }

    /**
     * Build a compact financial summary for the given user.
     * Target: ~300-500 tokens to keep API costs low.
     */
    public String buildSummary(UUID userId) {
        StringBuilder sb = new StringBuilder();

        appendAccountSummary(sb, userId);
        appendEnvelopeSummary(sb, userId);
        appendSpendingSummary(sb, userId);

        return sb.toString();
    }

    private void appendAccountSummary(StringBuilder sb, UUID userId) {
        List<BankAccountDTO> accounts = bankAccountService.getByAppUserId(userId);
        if (accounts.isEmpty()) {
            sb.append("ACCOUNTS: None\n");
            return;
        }

        sb.append("ACCOUNTS:\n");
        BigDecimal totalBalance = BigDecimal.ZERO;
        for (BankAccountDTO account : accounts) {
            sb.append("- ").append(account.getName())
                    .append(" (").append(account.getAccountType()).append(")")
                    .append(": $").append(account.getCurrentBalance().setScale(2))
                    .append("\n");
            totalBalance = totalBalance.add(account.getCurrentBalance());
        }
        sb.append("Total: $").append(totalBalance.setScale(2)).append("\n\n");
    }

    private void appendEnvelopeSummary(StringBuilder sb, UUID userId) {
        List<EnvelopeDTO> envelopes = envelopeService.getByAppUserId(userId);
        if (envelopes.isEmpty()) {
            sb.append("ENVELOPES: None\n");
            return;
        }

        // Get spent summaries for the current month
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        LocalDate monthEnd = now.withDayOfMonth(now.lengthOfMonth());
        List<EnvelopeSpentSummaryDTO> spentSummaries = envelopeService.getSpentSummaries(userId, monthStart, monthEnd);
        Map<UUID, EnvelopeSpentSummaryDTO> spentMap = spentSummaries.stream()
                .collect(Collectors.toMap(EnvelopeSpentSummaryDTO::getEnvelopeId, s -> s));

        sb.append("ENVELOPES (budget categories):\n");
        BigDecimal totalAllocated = BigDecimal.ZERO;
        for (EnvelopeDTO envelope : envelopes) {
            sb.append("- ").append(envelope.getName())
                    .append(": allocated $").append(envelope.getAllocatedBalance().setScale(2));

            EnvelopeSpentSummaryDTO spent = spentMap.get(envelope.getId());
            if (spent != null && spent.getPeriodSpent() != null
                    && spent.getPeriodSpent().compareTo(BigDecimal.ZERO) != 0) {
                sb.append(", spent this month $").append(spent.getPeriodSpent().abs().setScale(2));
                BigDecimal remaining = envelope.getAllocatedBalance().add(spent.getPeriodSpent());
                sb.append(", remaining $").append(remaining.setScale(2));
            }

            if (envelope.getGoalAmount() != null) {
                sb.append(", goal $").append(envelope.getGoalAmount().setScale(2));
                if (envelope.getGoalTargetDate() != null) {
                    sb.append(" by ").append(envelope.getGoalTargetDate());
                }
            }

            sb.append("\n");
            totalAllocated = totalAllocated.add(envelope.getAllocatedBalance());
        }
        sb.append("Total allocated: $").append(totalAllocated.setScale(2)).append("\n\n");
    }

    private void appendSpendingSummary(StringBuilder sb, UUID userId) {
        // Get last 30 days of transactions
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        List<TransactionDTO> transactions = transactionService
                .getByAppUserIdAndTransactionDateBetween(userId, startDate, endDate);

        if (transactions.isEmpty()) {
            sb.append("RECENT SPENDING (30 days): None\n");
            return;
        }

        // Aggregate by merchant/category to reduce tokens
        Map<String, SpendingAggregate> aggregates = new LinkedHashMap<>();
        for (TransactionDTO txn : transactions) {
            // Skip positive amounts (income/refunds) and special types
            if (txn.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                continue;
            }
            String key = determineCategory(txn);
            aggregates.computeIfAbsent(key, k -> new SpendingAggregate())
                    .add(txn.getAmount().abs());
        }

        if (aggregates.isEmpty()) {
            sb.append("RECENT SPENDING (30 days): No expenses\n");
            return;
        }

        // Sort by total spent descending, limit to top 10 categories
        List<Map.Entry<String, SpendingAggregate>> sorted = aggregates.entrySet().stream()
                .sorted((a, b) -> b.getValue().total.compareTo(a.getValue().total))
                .limit(10)
                .collect(Collectors.toList());

        sb.append("RECENT SPENDING (30 days, top categories):\n");
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (Map.Entry<String, SpendingAggregate> entry : sorted) {
            sb.append("- ").append(entry.getKey())
                    .append(": $").append(entry.getValue().total.setScale(2))
                    .append(" (").append(entry.getValue().count).append(" txns)\n");
            totalSpent = totalSpent.add(entry.getValue().total);
        }
        sb.append("Total spent: $").append(totalSpent.setScale(2)).append("\n");
    }

    private String determineCategory(TransactionDTO txn) {
        // Prefer Plaid category if available, fall back to merchant name, then
        // description
        if (txn.getPlaidCategory() != null && !txn.getPlaidCategory().isBlank()) {
            return txn.getPlaidCategory();
        }
        if (txn.getMerchantName() != null && !txn.getMerchantName().isBlank()) {
            return txn.getMerchantName();
        }
        if (txn.getDescription() != null && !txn.getDescription().isBlank()) {
            // Truncate long descriptions
            String desc = txn.getDescription();
            return desc.length() > 30 ? desc.substring(0, 30) : desc;
        }
        return "Uncategorized";
    }

    private static class SpendingAggregate {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        void add(BigDecimal amount) {
            total = total.add(amount);
            count++;
        }
    }
}
