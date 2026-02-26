package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.model.BankAccount;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnvelopeService {

    private final EnvelopeRepository envelopeRepository;
    private final AppUserRepository appUserRepository;

    public EnvelopeService(EnvelopeRepository envelopeRepository, AppUserRepository appUserRepository) {
        this.envelopeRepository = envelopeRepository;
        this.appUserRepository = appUserRepository;
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

    public void updateBalance(UUID id, BigDecimal balance) {
        Envelope e = envelopeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + id));
        BigDecimal updatedBalance = e.getAllocatedBalance().add(balance);
        e.setAllocatedBalance(updatedBalance);
        envelopeRepository.save(e);
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
                envelope.getCreatedAt()
        );
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
