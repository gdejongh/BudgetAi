package com.budget.budgetai.repository;

import com.budget.budgetai.model.PlaidItem;
import com.budget.budgetai.model.PlaidItemStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaidItemRepository extends JpaRepository<PlaidItem, UUID> {

    @EntityGraph(attributePaths = { "appUser" })
    List<PlaidItem> findByAppUserId(UUID appUserId);

    Optional<PlaidItem> findByItemId(String itemId);

    List<PlaidItem> findByStatus(PlaidItemStatus status);

    @EntityGraph(attributePaths = { "appUser" })
    Optional<PlaidItem> findByIdAndAppUserId(UUID id, UUID appUserId);
}
