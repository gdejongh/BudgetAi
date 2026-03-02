package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeAllocationDTO;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.model.EnvelopeAllocation;
import com.budget.budgetai.repository.EnvelopeAllocationRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnvelopeAllocationService {

    private final EnvelopeAllocationRepository allocationRepository;
    private final EnvelopeRepository envelopeRepository;

    public EnvelopeAllocationService(EnvelopeAllocationRepository allocationRepository,
            EnvelopeRepository envelopeRepository) {
        this.allocationRepository = allocationRepository;
        this.envelopeRepository = envelopeRepository;
    }

    /**
     * Set (upsert) the allocation for an envelope in a given month.
     */
    public EnvelopeAllocationDTO setAllocation(UUID envelopeId, LocalDate yearMonth, BigDecimal amount) {
        Envelope envelope = envelopeRepository.findById(envelopeId)
                .orElseThrow(() -> new EntityNotFoundException("Envelope not found with id: " + envelopeId));

        LocalDate normalizedMonth = yearMonth.withDayOfMonth(1);

        EnvelopeAllocation allocation = allocationRepository
                .findByEnvelopeIdAndYearMonth(envelopeId, normalizedMonth)
                .orElseGet(() -> {
                    EnvelopeAllocation newAlloc = new EnvelopeAllocation();
                    newAlloc.setEnvelope(envelope);
                    newAlloc.setYearMonth(normalizedMonth);
                    return newAlloc;
                });

        allocation.setAmount(amount);
        EnvelopeAllocation saved = allocationRepository.save(allocation);
        return toDTO(saved);
    }

    /**
     * Get all allocations for a user's envelopes in a specific month.
     */
    public List<EnvelopeAllocationDTO> getAllocationsForMonth(UUID userId, LocalDate yearMonth) {
        LocalDate normalizedMonth = yearMonth.withDayOfMonth(1);
        return allocationRepository.findByEnvelope_AppUser_IdAndYearMonth(userId, normalizedMonth)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get the full allocation history for a single envelope.
     */
    public List<EnvelopeAllocationDTO> getAllocationHistory(UUID envelopeId) {
        if (!envelopeRepository.existsById(envelopeId)) {
            throw new EntityNotFoundException("Envelope not found with id: " + envelopeId);
        }
        return allocationRepository.findByEnvelopeIdOrderByYearMonthDesc(envelopeId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create initial allocation for a newly created envelope.
     */
    public void createInitialAllocation(Envelope envelope, BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            EnvelopeAllocation allocation = new EnvelopeAllocation();
            allocation.setEnvelope(envelope);
            allocation.setYearMonth(LocalDate.now().withDayOfMonth(1));
            allocation.setAmount(amount);
            allocationRepository.save(allocation);
        }
    }

    /**
     * Get the total of all monthly allocations ever made for a specific envelope.
     */
    public BigDecimal getTotalAllocated(UUID envelopeId) {
        return allocationRepository.sumAllocationsByEnvelopeId(envelopeId);
    }

    /**
     * Add (or subtract) an amount to an envelope's allocation for a given month.
     * If no allocation exists for the month, one is created.
     */
    public void addToAllocation(UUID envelopeId, LocalDate yearMonth, BigDecimal amount) {
        Envelope envelope = envelopeRepository.findById(envelopeId)
                .orElseThrow(() -> new EntityNotFoundException("Envelope not found with id: " + envelopeId));

        LocalDate normalizedMonth = yearMonth.withDayOfMonth(1);

        EnvelopeAllocation allocation = allocationRepository
                .findByEnvelopeIdAndYearMonth(envelopeId, normalizedMonth)
                .orElseGet(() -> {
                    EnvelopeAllocation newAlloc = new EnvelopeAllocation();
                    newAlloc.setEnvelope(envelope);
                    newAlloc.setYearMonth(normalizedMonth);
                    newAlloc.setAmount(BigDecimal.ZERO);
                    return newAlloc;
                });

        allocation.setAmount(allocation.getAmount().add(amount));
        allocationRepository.save(allocation);
    }

    private EnvelopeAllocationDTO toDTO(EnvelopeAllocation entity) {
        return new EnvelopeAllocationDTO(
                entity.getEnvelope().getId(),
                entity.getYearMonth(),
                entity.getAmount());
    }
}
