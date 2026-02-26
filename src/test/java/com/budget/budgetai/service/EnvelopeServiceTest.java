package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvelopeServiceTest {

    @Mock
    private EnvelopeRepository envelopeRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private EnvelopeService envelopeService;

    private UUID envelopeId;
    private UUID userId;
    private AppUser appUser;
    private Envelope envelope;
    private EnvelopeDTO envelopeDTO;

    @BeforeEach
    void setUp() {
        envelopeId = UUID.randomUUID();
        userId = UUID.randomUUID();

        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");

        envelope = new Envelope();
        envelope.setId(envelopeId);
        envelope.setAppUser(appUser);
        envelope.setName("Groceries");
        envelope.setAllocatedBalance(new BigDecimal("500.00"));
        envelope.setCreatedAt(ZonedDateTime.now());

        envelopeDTO = new EnvelopeDTO(null, userId, "Groceries", new BigDecimal("500.00"), null);
    }

    // --- create ---

    @Test
    void create_happyPath_returnsSavedDTO() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(envelope);

        EnvelopeDTO result = envelopeService.create(envelopeDTO);

        assertNotNull(result);
        assertEquals(envelopeId, result.getId());
        assertEquals("Groceries", result.getName());
        assertEquals(new BigDecimal("500.00"), result.getAllocatedBalance());
        assertEquals(userId, result.getAppUserId());
    }

    @Test
    void create_nonExistentUser_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> envelopeService.create(envelopeDTO));
    }

    @Test
    void create_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> envelopeService.create(null));
    }

    @Test
    void create_nullName_throwsIllegalArgumentException() {
        envelopeDTO.setName(null);
        assertThrows(IllegalArgumentException.class, () -> envelopeService.create(envelopeDTO));
    }

    @Test
    void create_blankName_throwsIllegalArgumentException() {
        envelopeDTO.setName("   ");
        assertThrows(IllegalArgumentException.class, () -> envelopeService.create(envelopeDTO));
    }

    @Test
    void create_nullAllocatedBalance_throwsIllegalArgumentException() {
        envelopeDTO.setAllocatedBalance(null);
        assertThrows(IllegalArgumentException.class, () -> envelopeService.create(envelopeDTO));
    }

    @Test
    void create_nullAppUserId_throwsIllegalArgumentException() {
        envelopeDTO.setAppUserId(null);
        assertThrows(IllegalArgumentException.class, () -> envelopeService.create(envelopeDTO));
    }

    // --- getById ---

    @Test
    void getById_found_returnsDTO() {
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));

        EnvelopeDTO result = envelopeService.getById(envelopeId);

        assertNotNull(result);
        assertEquals(envelopeId, result.getId());
        assertEquals("Groceries", result.getName());
    }

    @Test
    void getById_notFound_throwsEntityNotFoundException() {
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> envelopeService.getById(envelopeId));
    }

    // --- getAll ---

    @Test
    void getAll_returnsAll() {
        when(envelopeRepository.findAll()).thenReturn(List.of(envelope));

        List<EnvelopeDTO> result = envelopeService.getAll();

        assertEquals(1, result.size());
        assertEquals("Groceries", result.get(0).getName());
    }

    @Test
    void getAll_empty_returnsEmptyList() {
        when(envelopeRepository.findAll()).thenReturn(Collections.emptyList());

        List<EnvelopeDTO> result = envelopeService.getAll();

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserId ---

    @Test
    void getByAppUserId_withResults_returnsDTOs() {
        when(envelopeRepository.findByAppUserId(userId)).thenReturn(List.of(envelope));

        List<EnvelopeDTO> result = envelopeService.getByAppUserId(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getAppUserId());
    }

    @Test
    void getByAppUserId_empty_returnsEmptyList() {
        when(envelopeRepository.findByAppUserId(userId)).thenReturn(Collections.emptyList());

        List<EnvelopeDTO> result = envelopeService.getByAppUserId(userId);

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserIdAndName ---

    @Test
    void getByAppUserIdAndName_withResults_returnsDTOs() {
        when(envelopeRepository.findByAppUserIdAndName(userId, "Groceries")).thenReturn(List.of(envelope));

        List<EnvelopeDTO> result = envelopeService.getByAppUserIdAndName(userId, "Groceries");

        assertEquals(1, result.size());
        assertEquals("Groceries", result.get(0).getName());
    }

    @Test
    void getByAppUserIdAndName_empty_returnsEmptyList() {
        when(envelopeRepository.findByAppUserIdAndName(userId, "NonExistent")).thenReturn(Collections.emptyList());

        List<EnvelopeDTO> result = envelopeService.getByAppUserIdAndName(userId, "NonExistent");

        assertTrue(result.isEmpty());
    }

    // --- update ---

    @Test
    void update_existing_updatesNameAndBalance() {
        EnvelopeDTO updateDTO = new EnvelopeDTO(null, userId, "Entertainment", new BigDecimal("200.00"), null);
        Envelope updatedEnvelope = new Envelope();
        updatedEnvelope.setId(envelopeId);
        updatedEnvelope.setAppUser(appUser);
        updatedEnvelope.setName("Entertainment");
        updatedEnvelope.setAllocatedBalance(new BigDecimal("200.00"));

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(updatedEnvelope);

        EnvelopeDTO result = envelopeService.update(envelopeId, updateDTO);

        assertNotNull(result);
        assertEquals("Entertainment", result.getName());
        assertEquals(new BigDecimal("200.00"), result.getAllocatedBalance());
    }

    @Test
    void update_nonExisting_throwsEntityNotFoundException() {
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> envelopeService.update(envelopeId, envelopeDTO));
    }

    @Test
    void update_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> envelopeService.update(envelopeId, null));
    }

    // --- delete ---

    @Test
    void delete_existing_deletesSuccessfully() {
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);

        envelopeService.delete(envelopeId);

        verify(envelopeRepository).deleteById(envelopeId);
    }

    @Test
    void delete_nonExisting_throwsEntityNotFoundException() {
        when(envelopeRepository.existsById(envelopeId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> envelopeService.delete(envelopeId));
    }

    @Test
    void updateBalance_existingEnvelope_updatesBalance() {
        UUID envelopeId = UUID.randomUUID();
        EnvelopeRepository repo = mock(EnvelopeRepository.class);
        AppUserRepository userRepo = mock(AppUserRepository.class);
        EnvelopeService service = new EnvelopeService(repo, userRepo);

        Envelope envelope = new Envelope();
        envelope.setId(envelopeId);
        envelope.setAllocatedBalance(new BigDecimal("100.00"));

        when(repo.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(repo.save(any(Envelope.class))).thenReturn(envelope);

        service.updateBalance(envelopeId, new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), envelope.getAllocatedBalance());
        verify(repo).save(envelope);
    }

    @Test
    void updateBalance_nonExistingEnvelope_throwsException() {
        UUID envelopeId = UUID.randomUUID();
        EnvelopeRepository repo = mock(EnvelopeRepository.class);
        AppUserRepository userRepo = mock(AppUserRepository.class);
        EnvelopeService service = new EnvelopeService(repo, userRepo);

        when(repo.findById(envelopeId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                service.updateBalance(envelopeId, new BigDecimal("10.00")));
    }
}
