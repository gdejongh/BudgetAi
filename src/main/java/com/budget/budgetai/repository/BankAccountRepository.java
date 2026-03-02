package com.budget.budgetai.repository;

import com.budget.budgetai.model.BankAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    @EntityGraph(attributePaths = { "appUser" })
    List<BankAccount> findByAppUserId(UUID appUserId);

    @EntityGraph(attributePaths = { "appUser" })
    List<BankAccount> findByAppUserIdAndName(UUID appUserId, String name);

    Optional<BankAccount> findByPlaidAccountId(String plaidAccountId);

    List<BankAccount> findByPlaidItemId(UUID plaidItemId);
}
