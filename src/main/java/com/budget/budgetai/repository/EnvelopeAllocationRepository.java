package com.budget.budgetai.repository;

import com.budget.budgetai.model.EnvelopeAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvelopeAllocationRepository extends JpaRepository<EnvelopeAllocation, UUID> {

    Optional<EnvelopeAllocation> findByEnvelopeIdAndYearMonth(UUID envelopeId, LocalDate yearMonth);

    List<EnvelopeAllocation> findByEnvelopeIdOrderByYearMonthDesc(UUID envelopeId);

    List<EnvelopeAllocation> findByEnvelope_AppUser_IdAndYearMonth(UUID userId, LocalDate yearMonth);

    /**
     * Sum all monthly allocations per envelope for a given user (all-time total).
     * Returns rows of [envelopeId, totalAllocated].
     */
    @Query("SELECT ea.envelope.id, COALESCE(SUM(ea.amount), 0) " +
            "FROM EnvelopeAllocation ea " +
            "WHERE ea.envelope.appUser.id = :userId " +
            "GROUP BY ea.envelope.id")
    List<Object[]> sumAllocationsByEnvelopeForUser(@Param("userId") UUID userId);

    /**
     * Sum all monthly allocations for a single envelope (all-time total).
     */
    @Query("SELECT COALESCE(SUM(ea.amount), 0) " +
            "FROM EnvelopeAllocation ea " +
            "WHERE ea.envelope.id = :envelopeId")
    BigDecimal sumAllocationsByEnvelopeId(@Param("envelopeId") UUID envelopeId);

    void deleteByEnvelopeId(UUID envelopeId);
}
