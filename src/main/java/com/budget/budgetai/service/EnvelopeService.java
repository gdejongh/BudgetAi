package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.dto.EnvelopeSpentSummaryDTO;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.repository.AppUserRepository;
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
    private final TransactionRepository transactionRepository;

    public EnvelopeService(EnvelopeRepository envelopeRepository, AppUserRepository appUserRepository,
            TransactionRepository transactionRepository) {
        this.envelopeRepository = envelopeRepository;
        this.appUserRepository = appUserRepository;
        this.transactionRepository = transactionRepository;
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
        Envelope envelope = toEntity(envelopeDTO);
        Envelope savedEnvelope = envelopeRepository.save(envelope);
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
        return envelopeRepository.findByAppUserId(appUserId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EnvelopeDTO> getByAppUserIdAndName(UUID appUserId, String name) {
        return envelopeRepository.findByAppUserIdAndName(appUserId, name).stream()
                .map(this::toDTO)
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
        envelope.setAllocatedBalance(envelopeDTO.getAllocatedBalance());
        Envelope updatedEnvelope = envelopeRepository.save(envelope);
        return toDTO(updatedEnvelope);
    }

    public void delete(UUID id) {
        if (!envelopeRepository.existsById(id)) {
            throw new EntityNotFoundException("Envelope not found with id: " + id);
        }
        envelopeRepository.deleteById(id);
    }

    // Mapper methods
    private EnvelopeDTO toDTO(Envelope envelope) {
        if (envelope == null) {
            return null;
        }
        return new EnvelopeDTO(
                envelope.getId(),
                envelope.getAppUser().getId(),
                envelope.getName(),
                envelope.getAllocatedBalance(),
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

        if (envelopeDTO.getAppUserId() != null) {
            if (!appUserRepository.existsById(envelopeDTO.getAppUserId())) {
                throw new EntityNotFoundException("AppUser not found with id: " + envelopeDTO.getAppUserId());
            }
            envelope.setAppUser(appUserRepository.getReferenceById(envelopeDTO.getAppUserId()));
        }

        return envelope;
    }
}
