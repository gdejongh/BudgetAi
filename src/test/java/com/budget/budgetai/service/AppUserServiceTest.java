package com.budget.budgetai.service;

import com.budget.budgetai.dto.AppUserDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EnvelopeCategoryService envelopeCategoryService;

    @InjectMocks
    private AppUserService appUserService;

    private UUID userId;
    private AppUser appUser;
    private AppUserDTO appUserDTO;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");
        appUser.setPasswordHash("hashedPassword");
        appUser.setCreatedAt(ZonedDateTime.now());

        appUserDTO = new AppUserDTO(null, "test@example.com", "plainPassword", null);
    }

    // --- create ---

    @Test
    void create_happyPath_returnsSavedDTO() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(appUser);

        AppUserDTO result = appUserService.create(appUserDTO);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("test@example.com", result.getEmail());
        verify(passwordEncoder).encode("plainPassword");
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void create_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> appUserService.create(null));
    }

    @Test
    void create_nullEmail_throwsIllegalArgumentException() {
        appUserDTO.setEmail(null);
        assertThrows(IllegalArgumentException.class, () -> appUserService.create(appUserDTO));
    }

    @Test
    void create_blankEmail_throwsIllegalArgumentException() {
        appUserDTO.setEmail("   ");
        assertThrows(IllegalArgumentException.class, () -> appUserService.create(appUserDTO));
    }

    @Test
    void create_nullPassword_throwsIllegalArgumentException() {
        appUserDTO.setPassword(null);
        assertThrows(IllegalArgumentException.class, () -> appUserService.create(appUserDTO));
    }

    @Test
    void create_blankPassword_throwsIllegalArgumentException() {
        appUserDTO.setPassword("   ");
        assertThrows(IllegalArgumentException.class, () -> appUserService.create(appUserDTO));
    }

    // --- getById ---

    @Test
    void getById_found_returnsDTO() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));

        AppUserDTO result = appUserService.getById(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void getById_notFound_throwsEntityNotFoundException() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> appUserService.getById(userId));
    }

    // --- getByEmail ---

    @Test
    void getByEmail_found_returnsDTO() {
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(appUser));

        AppUserDTO result = appUserService.getByEmail("test@example.com");

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void getByEmail_notFound_throwsEntityNotFoundException() {
        when(appUserRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> appUserService.getByEmail("nonexistent@example.com"));
    }

    // --- update ---

    @Test
    void update_existingUser_updatesEmail() {
        AppUserDTO updateDTO = new AppUserDTO(null, "new@example.com", null);
        AppUser updatedUser = new AppUser();
        updatedUser.setId(userId);
        updatedUser.setEmail("new@example.com");
        updatedUser.setPasswordHash("hashedPassword");

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));
        when(appUserRepository.save(any(AppUser.class))).thenReturn(updatedUser);

        AppUserDTO result = appUserService.update(userId, updateDTO);

        assertNotNull(result);
        assertEquals("new@example.com", result.getEmail());
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void update_nonExistingUser_throwsEntityNotFoundException() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> appUserService.update(userId, appUserDTO));
    }

    @Test
    void update_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> appUserService.update(userId, null));
    }

    // --- delete ---

    @Test
    void delete_existingUser_deletesSuccessfully() {
        when(appUserRepository.existsById(userId)).thenReturn(true);

        appUserService.delete(userId);

        verify(appUserRepository).deleteById(userId);
    }

    @Test
    void delete_nonExistingUser_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> appUserService.delete(userId));
    }
}
