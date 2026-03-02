package com.budget.budgetai.repository;

import com.budget.budgetai.model.EnvelopeCategory;
import com.budget.budgetai.model.EnvelopeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnvelopeCategoryRepository extends JpaRepository<EnvelopeCategory, UUID> {

    @EntityGraph(attributePaths = { "appUser" })
    List<EnvelopeCategory> findByAppUserId(UUID appUserId);

    @EntityGraph(attributePaths = { "appUser" })
    List<EnvelopeCategory> findByAppUserIdAndName(UUID appUserId, String name);

    @EntityGraph(attributePaths = { "appUser" })
    List<EnvelopeCategory> findByAppUserIdAndCategoryType(UUID appUserId, EnvelopeType categoryType);
}
