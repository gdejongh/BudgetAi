package com.budget.budgetai.repository;

import com.budget.budgetai.model.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @EntityGraph(attributePaths = { "appUser", "bankAccount", "envelope" })
    List<Transaction> findByAppUserId(UUID appUserId);

    @EntityGraph(attributePaths = { "appUser", "bankAccount", "envelope" })
    List<Transaction> findByBankAccountId(UUID bankAccountId);

    @EntityGraph(attributePaths = { "appUser", "bankAccount", "envelope" })
    List<Transaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = { "appUser", "bankAccount", "envelope" })
    List<Transaction> findByAppUserIdAndTransactionDateBetween(UUID appUserId, LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = { "appUser", "bankAccount", "envelope" })
    List<Transaction> findByEnvelopeId(UUID envelopeId);

    @Query("SELECT t.envelope.id, COALESCE(SUM(t.amount), 0) " +
            "FROM Transaction t " +
            "WHERE t.appUser.id = :appUserId AND t.envelope IS NOT NULL " +
            "GROUP BY t.envelope.id")
    List<Object[]> sumAmountByEnvelopeForUser(@Param("appUserId") UUID appUserId);

    @Query("SELECT t.envelope.id, COALESCE(SUM(t.amount), 0) " +
            "FROM Transaction t " +
            "WHERE t.appUser.id = :appUserId AND t.envelope IS NOT NULL " +
            "AND t.transactionDate BETWEEN :startDate AND :endDate " +
            "GROUP BY t.envelope.id")
    List<Object[]> sumAmountByEnvelopeForUserInDateRange(
            @Param("appUserId") UUID appUserId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
