package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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
        Envelope envelope = toEntity(envelopeDTO);
        Envelope savedEnvelope = envelopeRepository.save(envelope);
        return toDTO(savedEnvelope);
    }

    public EnvelopeDTO getById(UUID id) {
        return envelopeRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
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

    public EnvelopeDTO update(UUID id, EnvelopeDTO envelopeDTO) {
        Optional<Envelope> existingEnvelope = envelopeRepository.findById(id);
        if (existingEnvelope.isPresent()) {
            Envelope envelope = existingEnvelope.get();
            envelope.setName(envelopeDTO.getName());
            envelope.setAllocatedBalance(envelopeDTO.getAllocatedBalance());
            Envelope updatedEnvelope = envelopeRepository.save(envelope);
            return toDTO(updatedEnvelope);
        }
        return null;
    }

    public boolean delete(UUID id) {
        if (envelopeRepository.existsById(id)) {
            envelopeRepository.deleteById(id);
            return true;
        }
        return false;
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
            Optional<AppUser> appUser = appUserRepository.findById(envelopeDTO.getAppUserId());
            appUser.ifPresent(envelope::setAppUser);
        }

        return envelope;
    }
}
