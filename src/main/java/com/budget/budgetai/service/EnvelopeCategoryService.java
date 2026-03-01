package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeCategoryDTO;
import com.budget.budgetai.model.EnvelopeCategory;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.EnvelopeCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnvelopeCategoryService {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Bills", "Savings", "Food & Dining", "Entertainment", "Transportation");

    private final EnvelopeCategoryRepository envelopeCategoryRepository;
    private final AppUserRepository appUserRepository;

    public EnvelopeCategoryService(EnvelopeCategoryRepository envelopeCategoryRepository,
            AppUserRepository appUserRepository) {
        this.envelopeCategoryRepository = envelopeCategoryRepository;
        this.appUserRepository = appUserRepository;
    }

    public EnvelopeCategoryDTO create(EnvelopeCategoryDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("EnvelopeCategoryDTO cannot be null");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Category name cannot be null or empty");
        }
        if (dto.getAppUserId() == null) {
            throw new IllegalArgumentException("App user ID cannot be null");
        }
        EnvelopeCategory category = toEntity(dto);
        EnvelopeCategory saved = envelopeCategoryRepository.save(category);
        return toDTO(saved);
    }

    public EnvelopeCategoryDTO getById(UUID id) {
        return envelopeCategoryRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Envelope category not found with id: " + id));
    }

    public List<EnvelopeCategoryDTO> getByAppUserId(UUID appUserId) {
        return envelopeCategoryRepository.findByAppUserId(appUserId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EnvelopeCategoryDTO> getByAppUserIdAndName(UUID appUserId, String name) {
        return envelopeCategoryRepository.findByAppUserIdAndName(appUserId, name).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public EnvelopeCategoryDTO update(UUID id, EnvelopeCategoryDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("EnvelopeCategoryDTO cannot be null");
        }
        EnvelopeCategory category = envelopeCategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Envelope category not found with id: " + id));
        category.setName(dto.getName());
        EnvelopeCategory updated = envelopeCategoryRepository.save(category);
        return toDTO(updated);
    }

    public void delete(UUID id) {
        if (!envelopeCategoryRepository.existsById(id)) {
            throw new EntityNotFoundException("Envelope category not found with id: " + id);
        }
        envelopeCategoryRepository.deleteById(id);
    }

    public List<EnvelopeCategoryDTO> seedDefaultCategories(UUID appUserId) {
        if (appUserId == null) {
            throw new IllegalArgumentException("App user ID cannot be null");
        }
        if (!appUserRepository.existsById(appUserId)) {
            throw new EntityNotFoundException("AppUser not found with id: " + appUserId);
        }
        return DEFAULT_CATEGORIES.stream()
                .map(name -> {
                    EnvelopeCategoryDTO dto = new EnvelopeCategoryDTO();
                    dto.setAppUserId(appUserId);
                    dto.setName(name);
                    return create(dto);
                })
                .collect(Collectors.toList());
    }

    // Mapper methods
    private EnvelopeCategoryDTO toDTO(EnvelopeCategory category) {
        if (category == null) {
            return null;
        }
        return new EnvelopeCategoryDTO(
                category.getId(),
                category.getAppUser().getId(),
                category.getName(),
                category.getCreatedAt());
    }

    private EnvelopeCategory toEntity(EnvelopeCategoryDTO dto) {
        if (dto == null) {
            return null;
        }
        EnvelopeCategory category = new EnvelopeCategory();
        category.setId(dto.getId());
        category.setName(dto.getName());

        if (dto.getAppUserId() != null) {
            if (!appUserRepository.existsById(dto.getAppUserId())) {
                throw new EntityNotFoundException("AppUser not found with id: " + dto.getAppUserId());
            }
            category.setAppUser(appUserRepository.getReferenceById(dto.getAppUserId()));
        }

        return category;
    }
}
