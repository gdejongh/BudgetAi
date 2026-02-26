package com.budget.budgetai.repository;

import com.budget.budgetai.model.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @EntityGraph(attributePaths = {"appUser", "bankAccount", "envelope"})
    List<Transaction> findByAppUserId(UUID appUserId);

    @EntityGraph(attributePaths = {"appUser", "bankAccount", "envelope"})
    List<Transaction> findByBankAccountId(UUID bankAccountId);

    @EntityGraph(attributePaths = {"appUser", "bankAccount", "envelope"})
    List<Transaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"appUser", "bankAccount", "envelope"})
    List<Transaction> findByAppUserIdAndTransactionDateBetween(UUID appUserId, LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"appUser", "bankAccount", "envelope"})
    List<Transaction> findByEnvelopeId(UUID envelopeId);
}
