package com.budget.budgetai.repository;

import com.budget.budgetai.model.AiAdviceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiAdviceCacheRepository extends JpaRepository<AiAdviceCache, UUID> {

    Optional<AiAdviceCache> findByAppUserId(UUID appUserId);

    void deleteByAppUserId(UUID appUserId);

    void deleteByExpiresAtBefore(ZonedDateTime dateTime);
}
