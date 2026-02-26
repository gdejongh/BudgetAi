package com.budget.budgetai.repository;

import com.budget.budgetai.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAppUserId(UUID appUserId);

    List<Transaction> findByBankAccountId(UUID bankAccountId);

    List<Transaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    List<Transaction> findByAppUserIdAndTransactionDateBetween(UUID appUserId, LocalDate startDate, LocalDate endDate);

    List<Transaction> findByEnvelopeId(UUID envelopeId);
}
