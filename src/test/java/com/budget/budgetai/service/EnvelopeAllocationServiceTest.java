package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeAllocationDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.model.EnvelopeAllocation;
import com.budget.budgetai.model.EnvelopeCategory;
import com.budget.budgetai.repository.EnvelopeAllocationRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvelopeAllocationServiceTest {

    @Mock
    private EnvelopeAllocationRepository allocationRepository;

    @Mock
    private EnvelopeRepository envelopeRepository;

    @InjectMocks
    private EnvelopeAllocationService allocationService;

    private UUID envelopeId;
    private UUID userId;
    private Envelope envelope;
    private LocalDate march2026;

    @BeforeEach
    void setUp() {
        envelopeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        march2026 = LocalDate.of(2026, 3, 1);

        AppUser user = new AppUser();
        user.setId(userId);

        EnvelopeCategory category = new EnvelopeCategory();
        category.setId(UUID.randomUUID());

        envelope = new Envelope();
        envelope.setId(envelopeId);
        envelope.setAppUser(user);
        envelope.setEnvelopeCategory(category);
        envelope.setName("Groceries");
        envelope.setAllocatedBalance(new BigDecimal("500.00"));
    }

    // --- setAllocation ---

    @Test
    void setAllocation_newMonth_createsAllocation() {
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(allocationRepository.findByEnvelopeIdAndYearMonth(envelopeId, march2026))
                .thenReturn(Optional.empty());

        EnvelopeAllocation saved = new EnvelopeAllocation();
        saved.setId(UUID.randomUUID());
        saved.setEnvelope(envelope);
        saved.setYearMonth(march2026);
        saved.setAmount(new BigDecimal("150.00"));
        when(allocationRepository.save(any(EnvelopeAllocation.class))).thenReturn(saved);

        EnvelopeAllocationDTO result = allocationService.setAllocation(
                envelopeId, march2026, new BigDecimal("150.00"));

        assertNotNull(result);
        assertEquals(envelopeId, result.getEnvelopeId());
        assertEquals(march2026, result.getYearMonth());
        assertEquals(new BigDecimal("150.00"), result.getAmount());
    }

    @Test
    void setAllocation_existingMonth_updatesAllocation() {
        EnvelopeAllocation existing = new EnvelopeAllocation();
        existing.setId(UUID.randomUUID());
        existing.setEnvelope(envelope);
        existing.setYearMonth(march2026);
        existing.setAmount(new BigDecimal("100.00"));

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(allocationRepository.findByEnvelopeIdAndYearMonth(envelopeId, march2026))
                .thenReturn(Optional.of(existing));

        EnvelopeAllocation saved = new EnvelopeAllocation();
        saved.setId(existing.getId());
        saved.setEnvelope(envelope);
        saved.setYearMonth(march2026);
        saved.setAmount(new BigDecimal("200.00"));
        when(allocationRepository.save(any(EnvelopeAllocation.class))).thenReturn(saved);

        EnvelopeAllocationDTO result = allocationService.setAllocation(
                envelopeId, march2026, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("200.00"), result.getAmount());
    }

    @Test
    void setAllocation_midMonthDate_normalizesToFirstOfMonth() {
        LocalDate midMonth = LocalDate.of(2026, 3, 15);
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(allocationRepository.findByEnvelopeIdAndYearMonth(envelopeId, march2026))
                .thenReturn(Optional.empty());

        EnvelopeAllocation saved = new EnvelopeAllocation();
        saved.setId(UUID.randomUUID());
        saved.setEnvelope(envelope);
        saved.setYearMonth(march2026);
        saved.setAmount(new BigDecimal("75.00"));
        when(allocationRepository.save(any(EnvelopeAllocation.class))).thenReturn(saved);

        EnvelopeAllocationDTO result = allocationService.setAllocation(
                envelopeId, midMonth, new BigDecimal("75.00"));

        assertEquals(march2026, result.getYearMonth());
        verify(allocationRepository).findByEnvelopeIdAndYearMonth(envelopeId, march2026);
    }

    @Test
    void setAllocation_negativeAmount_createsAllocation_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allocationService.setAllocation(
                        envelopeId, march2026, new BigDecimal("-20.00")));
        assertEquals("Allocation amount cannot be negative", ex.getMessage());
    }

    @Test
    void setAllocation_negativeAmount_updatesExistingAllocation_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allocationService.setAllocation(
                        envelopeId, march2026, new BigDecimal("-20.00")));
        assertEquals("Allocation amount cannot be negative", ex.getMessage());
    }

    @Test
    void setAllocation_envelopeNotFound_throwsEntityNotFoundException() {
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> allocationService.setAllocation(envelopeId, march2026, new BigDecimal("100.00")));
    }

    // --- getAllocationsForMonth ---

    @Test
    void getAllocationsForMonth_returnsAllocations() {
        EnvelopeAllocation alloc = new EnvelopeAllocation();
        alloc.setId(UUID.randomUUID());
        alloc.setEnvelope(envelope);
        alloc.setYearMonth(march2026);
        alloc.setAmount(new BigDecimal("200.00"));

        when(allocationRepository.findByEnvelope_AppUser_IdAndYearMonth(userId, march2026))
                .thenReturn(List.of(alloc));

        List<EnvelopeAllocationDTO> result = allocationService.getAllocationsForMonth(userId, march2026);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("200.00"), result.get(0).getAmount());
    }

    @Test
    void getAllocationsForMonth_empty_returnsEmptyList() {
        when(allocationRepository.findByEnvelope_AppUser_IdAndYearMonth(userId, march2026))
                .thenReturn(Collections.emptyList());

        List<EnvelopeAllocationDTO> result = allocationService.getAllocationsForMonth(userId, march2026);

        assertTrue(result.isEmpty());
    }

    // --- getAllocationHistory ---

    @Test
    void getAllocationHistory_returnsHistory() {
        EnvelopeAllocation alloc1 = new EnvelopeAllocation();
        alloc1.setId(UUID.randomUUID());
        alloc1.setEnvelope(envelope);
        alloc1.setYearMonth(march2026);
        alloc1.setAmount(new BigDecimal("150.00"));

        EnvelopeAllocation alloc2 = new EnvelopeAllocation();
        alloc2.setId(UUID.randomUUID());
        alloc2.setEnvelope(envelope);
        alloc2.setYearMonth(LocalDate.of(2026, 2, 1));
        alloc2.setAmount(new BigDecimal("100.00"));

        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(allocationRepository.findByEnvelopeIdOrderByYearMonthDesc(envelopeId))
                .thenReturn(List.of(alloc1, alloc2));

        List<EnvelopeAllocationDTO> result = allocationService.getAllocationHistory(envelopeId);

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("150.00"), result.get(0).getAmount());
        assertEquals(new BigDecimal("100.00"), result.get(1).getAmount());
    }

    @Test
    void getAllocationHistory_envelopeNotFound_throwsEntityNotFoundException() {
        when(envelopeRepository.existsById(envelopeId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> allocationService.getAllocationHistory(envelopeId));
    }

    // --- createInitialAllocation ---

    @Test
    void createInitialAllocation_positiveAmount_createsAllocation() {
        when(allocationRepository.save(any(EnvelopeAllocation.class))).thenAnswer(i -> i.getArgument(0));

        allocationService.createInitialAllocation(envelope, new BigDecimal("500.00"));

        verify(allocationRepository).save(any(EnvelopeAllocation.class));
    }

    @Test
    void createInitialAllocation_zeroAmount_doesNotCreate() {
        allocationService.createInitialAllocation(envelope, BigDecimal.ZERO);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void createInitialAllocation_nullAmount_doesNotCreate() {
        allocationService.createInitialAllocation(envelope, null);

        verify(allocationRepository, never()).save(any());
    }

    // --- getTotalAllocated ---

    @Test
    void getTotalAllocated_returnsSumOfAllAllocations() {
        when(allocationRepository.sumAllocationsByEnvelopeId(envelopeId))
                .thenReturn(new BigDecimal("750.00"));

        BigDecimal result = allocationService.getTotalAllocated(envelopeId);

        assertEquals(new BigDecimal("750.00"), result);
    }

    // --- setAllocation validation ---

    @Test
    void setAllocation_withNegativeAmount_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> allocationService.setAllocation(envelopeId, LocalDate.of(2026, 3, 1), new BigDecimal("-10.00")));
        assertEquals("Allocation amount cannot be negative", ex.getMessage());
    }
}
