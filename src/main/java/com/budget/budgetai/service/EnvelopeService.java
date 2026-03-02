package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.dto.EnvelopeSpentSummaryDTO;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.model.EnvelopeType;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
import com.budget.budgetai.repository.EnvelopeAllocationRepository;
import com.budget.budgetai.repository.EnvelopeCategoryRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import com.budget.budgetai.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnvelopeService {

    private final EnvelopeRepository envelopeRepository;
    private final AppUserRepository appUserRepository;
    private final BankAccountRepository bankAccountRepository;
    private final EnvelopeCategoryRepository envelopeCategoryRepository;
    private final TransactionRepository transactionRepository;
    private final EnvelopeAllocationRepository envelopeAllocationRepository;
    private final EnvelopeAllocationService envelopeAllocationService;

    public EnvelopeService(EnvelopeRepository envelopeRepository, AppUserRepository appUserRepository,
            BankAccountRepository bankAccountRepository,
            EnvelopeCategoryRepository envelopeCategoryRepository,
            TransactionRepository transactionRepository,
            EnvelopeAllocationRepository envelopeAllocationRepository,
            EnvelopeAllocationService envelopeAllocationService) {
        this.envelopeRepository = envelopeRepository;
        this.appUserRepository = appUserRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.envelopeCategoryRepository = envelopeCategoryRepository;
        this.transactionRepository = transactionRepository;
        this.envelopeAllocationRepository = envelopeAllocationRepository;
        this.envelopeAllocationService = envelopeAllocationService;
    }

    public EnvelopeDTO create(EnvelopeDTO envelopeDTO) {
        if (envelopeDTO == null) {
            throw new IllegalArgumentException("EnvelopeDTO cannot be null");
        }
        if (envelopeDTO.getName() == null || envelopeDTO.getName().isBlank()) {
            throw new IllegalArgumentException("Envelope name cannot be null or empty");
        }
        if (envelopeDTO.getAllocatedBalance() == null) {
            throw new IllegalArgumentException("Allocated balance cannot be null");
        }
        if (envelopeDTO.getAppUserId() == null) {
            throw new IllegalArgumentException("App user ID cannot be null");
        }
        if (envelopeDTO.getEnvelopeCategoryId() == null) {
            throw new IllegalArgumentException("Envelope category ID cannot be null");
        }
        Envelope envelope = toEntity(envelopeDTO);
        Envelope savedEnvelope = envelopeRepository.save(envelope);

        // Create initial monthly allocation for the current month
        envelopeAllocationService.createInitialAllocation(savedEnvelope, envelopeDTO.getAllocatedBalance());

        return toDTO(savedEnvelope);
    }

    public EnvelopeDTO getById(UUID id) {
        return envelopeRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Envelope not found with id: " + id));
    }

    public List<EnvelopeDTO> getAll() {
        return envelopeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EnvelopeDTO> getByAppUserId(UUID appUserId) {
        List<Envelope> envelopes = envelopeRepository.findByAppUserId(appUserId);
        // Build a map of envelopeId -> total allocated (sum of all monthly allocations)
        Map<UUID, BigDecimal> totalAllocMap = buildTotalAllocationMap(appUserId);
        return envelopes.stream()
                .map(e -> toDTO(e, totalAllocMap.getOrDefault(e.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    public List<EnvelopeDTO> getByAppUserIdAndName(UUID appUserId, String name) {
        List<Envelope> envelopes = envelopeRepository.findByAppUserIdAndName(appUserId, name);
        Map<UUID, BigDecimal> totalAllocMap = buildTotalAllocationMap(appUserId);
        return envelopes.stream()
                .map(e -> toDTO(e, totalAllocMap.getOrDefault(e.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    public List<EnvelopeSpentSummaryDTO> getSpentSummaries(UUID appUserId, LocalDate startDate, LocalDate endDate) {
        // Get all-time spent per envelope
        List<Object[]> allTimeRows = transactionRepository.sumAmountByEnvelopeForUser(appUserId);
        Map<UUID, BigDecimal> allTimeMap = new HashMap<>();
        for (Object[] row : allTimeRows) {
            allTimeMap.put((UUID) row[0], (BigDecimal) row[1]);
        }

        // Get period spent per envelope
        List<Object[]> periodRows = transactionRepository.sumAmountByEnvelopeForUserInDateRange(appUserId, startDate,
                endDate);
        Map<UUID, BigDecimal> periodMap = new HashMap<>();
        for (Object[] row : periodRows) {
            periodMap.put((UUID) row[0], (BigDecimal) row[1]);
        }

        // Merge into a list covering all envelopes that have any spending
        Set<UUID> envelopeIds = new HashSet<>(allTimeMap.keySet());
        envelopeIds.addAll(periodMap.keySet());

        return envelopeIds.stream()
                .map(id -> new EnvelopeSpentSummaryDTO(
                        id,
                        allTimeMap.getOrDefault(id, BigDecimal.ZERO),
                        periodMap.getOrDefault(id, BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    public EnvelopeDTO update(UUID id, EnvelopeDTO envelopeDTO) {
        if (envelopeDTO == null) {
            throw new IllegalArgumentException("EnvelopeDTO cannot be null");
        }
        Envelope envelope = envelopeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Envelope not found with id: " + id));
        envelope.setName(envelopeDTO.getName());
        // Note: allocatedBalance is now managed via the envelope_allocation table.
        // The entity's allocatedBalance field is kept for backward compatibility but
        // is no longer the source of truth.
        if (envelopeDTO.getEnvelopeCategoryId() != null) {
            if (!envelopeCategoryRepository.existsById(envelopeDTO.getEnvelopeCategoryId())) {
                throw new EntityNotFoundException(
                        "EnvelopeCategory not found with id: " + envelopeDTO.getEnvelopeCategoryId());
            }
            envelope.setEnvelopeCategory(
                    envelopeCategoryRepository.getReferenceById(envelopeDTO.getEnvelopeCategoryId()));
        }
        Envelope updatedEnvelope = envelopeRepository.save(envelope);
        return toDTO(updatedEnvelope);
    }

    public void delete(UUID id) {
        Envelope envelope = envelopeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Envelope not found with id: " + id));
        if (envelope.getEnvelopeType() == EnvelopeType.CC_PAYMENT) {
            throw new IllegalStateException(
                    "Cannot delete a CC Payment envelope. Delete the linked credit card account instead.");
        }
        envelopeRepository.deleteById(id);
    }

    private Map<UUID, BigDecimal> buildTotalAllocationMap(UUID appUserId) {
        List<Object[]> rows = envelopeAllocationRepository.sumAllocationsByEnvelopeForUser(appUserId);
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    // Mapper methods

    /**
     * Convert entity to DTO. allocatedBalance = sum of all monthly allocations.
     */
    private EnvelopeDTO toDTO(Envelope envelope) {
        if (envelope == null) {
            return null;
        }
        BigDecimal totalAllocated = envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelope.getId());
        return new EnvelopeDTO(
                envelope.getId(),
                envelope.getAppUser().getId(),
                envelope.getEnvelopeCategory() != null ? envelope.getEnvelopeCategory().getId() : null,
                envelope.getName(),
                totalAllocated,
                envelope.getEnvelopeType() != null ? envelope.getEnvelopeType().name() : EnvelopeType.STANDARD.name(),
                envelope.getLinkedAccount() != null ? envelope.getLinkedAccount().getId() : null,
                envelope.getCreatedAt());
    }

    /**
     * Convert entity to DTO with a pre-computed total allocation.
     */
    private EnvelopeDTO toDTO(Envelope envelope, BigDecimal totalAllocated) {
        if (envelope == null) {
            return null;
        }
        return new EnvelopeDTO(
                envelope.getId(),
                envelope.getAppUser().getId(),
                envelope.getEnvelopeCategory() != null ? envelope.getEnvelopeCategory().getId() : null,
                envelope.getName(),
                totalAllocated,
                envelope.getEnvelopeType() != null ? envelope.getEnvelopeType().name() : EnvelopeType.STANDARD.name(),
                envelope.getLinkedAccount() != null ? envelope.getLinkedAccount().getId() : null,
                envelope.getCreatedAt());
    }

    private Envelope toEntity(EnvelopeDTO envelopeDTO) {
        if (envelopeDTO == null) {
            return null;
        }
        Envelope envelope = new Envelope();
        envelope.setId(envelopeDTO.getId());
        envelope.setName(envelopeDTO.getName());
        envelope.setAllocatedBalance(envelopeDTO.getAllocatedBalance());

        if (envelopeDTO.getEnvelopeType() != null) {
            envelope.setEnvelopeType(EnvelopeType.valueOf(envelopeDTO.getEnvelopeType()));
        }

        if (envelopeDTO.getLinkedAccountId() != null) {
            if (!bankAccountRepository.existsById(envelopeDTO.getLinkedAccountId())) {
                throw new EntityNotFoundException("BankAccount not found with id: " + envelopeDTO.getLinkedAccountId());
            }
            envelope.setLinkedAccount(bankAccountRepository.getReferenceById(envelopeDTO.getLinkedAccountId()));
        }

        if (envelopeDTO.getAppUserId() != null) {
            if (!appUserRepository.existsById(envelopeDTO.getAppUserId())) {
                throw new EntityNotFoundException("AppUser not found with id: " + envelopeDTO.getAppUserId());
            }
            envelope.setAppUser(appUserRepository.getReferenceById(envelopeDTO.getAppUserId()));
        }

        if (envelopeDTO.getEnvelopeCategoryId() != null) {
            if (!envelopeCategoryRepository.existsById(envelopeDTO.getEnvelopeCategoryId())) {
                throw new EntityNotFoundException(
                        "EnvelopeCategory not found with id: " + envelopeDTO.getEnvelopeCategoryId());
            }
            envelope.setEnvelopeCategory(
                    envelopeCategoryRepository.getReferenceById(envelopeDTO.getEnvelopeCategoryId()));
        }

        return envelope;
    }
}
