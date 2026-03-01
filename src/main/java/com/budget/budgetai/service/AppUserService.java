package com.budget.budgetai.service;

import com.budget.budgetai.dto.AppUserDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnvelopeCategoryService envelopeCategoryService;

    public AppUserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
            EnvelopeCategoryService envelopeCategoryService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.envelopeCategoryService = envelopeCategoryService;
    }

    public AppUserDTO create(AppUserDTO appUserDTO) {
        if (appUserDTO == null) {
            throw new IllegalArgumentException("AppUserDTO cannot be null");
        }
        if (appUserDTO.getEmail() == null || appUserDTO.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (appUserDTO.getPassword() == null || appUserDTO.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        AppUser appUser = toEntity(appUserDTO);
        AppUser savedUser = appUserRepository.save(appUser);
        AppUserDTO result = toDTO(savedUser);
        envelopeCategoryService.seedDefaultCategories(savedUser.getId());
        return result;
    }

    public AppUserDTO getById(UUID id) {
        return appUserRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("AppUser not found with id: " + id));
    }

    public AppUserDTO getByEmail(String email) {
        return appUserRepository.findByEmail(email)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("AppUser not found with email: " + email));
    }

    public List<AppUserDTO> getAll() {
        return appUserRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public AppUserDTO update(UUID id, AppUserDTO appUserDTO) {
        if (appUserDTO == null) {
            throw new IllegalArgumentException("AppUserDTO cannot be null");
        }
        AppUser appUser = appUserRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("AppUser not found with id: " + id));
        appUser.setEmail(appUserDTO.getEmail());
        AppUser updatedUser = appUserRepository.save(appUser);
        return toDTO(updatedUser);
    }

    public void delete(UUID id) {
        if (!appUserRepository.existsById(id)) {
            throw new EntityNotFoundException("AppUser not found with id: " + id);
        }
        appUserRepository.deleteById(id);
    }

    // Mapper methods
    private AppUserDTO toDTO(AppUser appUser) {
        if (appUser == null) {
            return null;
        }
        return new AppUserDTO(appUser.getId(), appUser.getEmail(), appUser.getCreatedAt());
    }

    private AppUser toEntity(AppUserDTO appUserDTO) {
        if (appUserDTO == null) {
            return null;
        }
        AppUser appUser = new AppUser();
        appUser.setId(appUserDTO.getId());
        appUser.setEmail(appUserDTO.getEmail());
        appUser.setPasswordHash(passwordEncoder.encode(appUserDTO.getPassword()));
        return appUser;
    }
}
