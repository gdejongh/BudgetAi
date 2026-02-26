package com.budget.budgetai.service;

import com.budget.budgetai.dto.AppUserDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppUserService {

    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AppUserDTO create(AppUserDTO appUserDTO) {
        AppUser appUser = toEntity(appUserDTO);
        AppUser savedUser = appUserRepository.save(appUser);
        return toDTO(savedUser);
    }

    public AppUserDTO getById(UUID id) {
        return appUserRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public AppUserDTO getByEmail(String email) {
        return appUserRepository.findByEmail(email)
                .map(this::toDTO)
                .orElse(null);
    }

    public List<AppUserDTO> getAll() {
        return appUserRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public AppUserDTO update(UUID id, AppUserDTO appUserDTO) {
        Optional<AppUser> existingUser = appUserRepository.findById(id);
        if (existingUser.isPresent()) {
            AppUser appUser = existingUser.get();
            appUser.setEmail(appUserDTO.getEmail());
            AppUser updatedUser = appUserRepository.save(appUser);
            return toDTO(updatedUser);
        }
        return null;
    }

    public boolean delete(UUID id) {
        if (appUserRepository.existsById(id)) {
            appUserRepository.deleteById(id);
            return true;
        }
        return false;
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
        return appUser;
    }
}
