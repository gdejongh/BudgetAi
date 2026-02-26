package com.budget.budgetai.repository;

import com.budget.budgetai.model.Envelope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnvelopeRepository extends JpaRepository<Envelope, UUID> {

    List<Envelope> findByAppUserId(UUID appUserId);

    List<Envelope> findByAppUserIdAndName(UUID appUserId, String name);
}
