package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeCategoryDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.EnvelopeCategory;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.EnvelopeCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvelopeCategoryServiceTest {

    @Mock
    private EnvelopeCategoryRepository envelopeCategoryRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private EnvelopeCategoryService envelopeCategoryService;

    private UUID categoryId;
    private UUID userId;
    private AppUser appUser;
    private EnvelopeCategory category;
    private EnvelopeCategoryDTO categoryDTO;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        userId = UUID.randomUUID();

        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");

        category = new EnvelopeCategory();
        category.setId(categoryId);
        category.setAppUser(appUser);
        category.setName("Bills");
        category.setCreatedAt(ZonedDateTime.now());

        categoryDTO = new EnvelopeCategoryDTO(null, userId, "Bills", null);
    }

    // --- create ---

    @Test
    void create_happyPath_returnsSavedDTO() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(envelopeCategoryRepository.save(any(EnvelopeCategory.class))).thenReturn(category);

        EnvelopeCategoryDTO result = envelopeCategoryService.create(categoryDTO);

        assertNotNull(result);
        assertEquals(categoryId, result.getId());
        assertEquals("Bills", result.getName());
        assertEquals(userId, result.getAppUserId());
    }

    @Test
    void create_nonExistentUser_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> envelopeCategoryService.create(categoryDTO));
    }

    @Test
    void create_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> envelopeCategoryService.create(null));
    }

    @Test
    void create_nullName_throwsIllegalArgumentException() {
        categoryDTO.setName(null);
        assertThrows(IllegalArgumentException.class, () -> envelopeCategoryService.create(categoryDTO));
    }

    @Test
    void create_blankName_throwsIllegalArgumentException() {
        categoryDTO.setName("   ");
        assertThrows(IllegalArgumentException.class, () -> envelopeCategoryService.create(categoryDTO));
    }

    @Test
    void create_nullAppUserId_throwsIllegalArgumentException() {
        categoryDTO.setAppUserId(null);
        assertThrows(IllegalArgumentException.class, () -> envelopeCategoryService.create(categoryDTO));
    }

    // --- getById ---

    @Test
    void getById_found_returnsDTO() {
        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        EnvelopeCategoryDTO result = envelopeCategoryService.getById(categoryId);

        assertNotNull(result);
        assertEquals(categoryId, result.getId());
        assertEquals("Bills", result.getName());
    }

    @Test
    void getById_notFound_throwsEntityNotFoundException() {
        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> envelopeCategoryService.getById(categoryId));
    }

    // --- getByAppUserId ---

    @Test
    void getByAppUserId_withResults_returnsDTOs() {
        when(envelopeCategoryRepository.findByAppUserId(userId)).thenReturn(List.of(category));

        List<EnvelopeCategoryDTO> result = envelopeCategoryService.getByAppUserId(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getAppUserId());
    }

    @Test
    void getByAppUserId_empty_returnsEmptyList() {
        when(envelopeCategoryRepository.findByAppUserId(userId)).thenReturn(Collections.emptyList());

        List<EnvelopeCategoryDTO> result = envelopeCategoryService.getByAppUserId(userId);

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserIdAndName ---

    @Test
    void getByAppUserIdAndName_withResults_returnsDTOs() {
        when(envelopeCategoryRepository.findByAppUserIdAndName(userId, "Bills")).thenReturn(List.of(category));

        List<EnvelopeCategoryDTO> result = envelopeCategoryService.getByAppUserIdAndName(userId, "Bills");

        assertEquals(1, result.size());
        assertEquals("Bills", result.get(0).getName());
    }

    @Test
    void getByAppUserIdAndName_empty_returnsEmptyList() {
        when(envelopeCategoryRepository.findByAppUserIdAndName(userId, "NonExistent"))
                .thenReturn(Collections.emptyList());

        List<EnvelopeCategoryDTO> result = envelopeCategoryService.getByAppUserIdAndName(userId, "NonExistent");

        assertTrue(result.isEmpty());
    }

    // --- update ---

    @Test
    void update_existing_updatesName() {
        EnvelopeCategoryDTO updateDTO = new EnvelopeCategoryDTO(null, userId, "Utilities", null);
        EnvelopeCategory updatedCategory = new EnvelopeCategory();
        updatedCategory.setId(categoryId);
        updatedCategory.setAppUser(appUser);
        updatedCategory.setName("Utilities");

        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(envelopeCategoryRepository.save(any(EnvelopeCategory.class))).thenReturn(updatedCategory);

        EnvelopeCategoryDTO result = envelopeCategoryService.update(categoryId, updateDTO);

        assertNotNull(result);
        assertEquals("Utilities", result.getName());
    }

    @Test
    void update_nonExisting_throwsEntityNotFoundException() {
        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> envelopeCategoryService.update(categoryId, categoryDTO));
    }

    @Test
    void update_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> envelopeCategoryService.update(categoryId, null));
    }

    // --- delete ---

    @Test
    void delete_existing_deletesSuccessfully() {
        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        envelopeCategoryService.delete(categoryId);

        verify(envelopeCategoryRepository).deleteById(categoryId);
    }

    @Test
    void delete_nonExisting_throwsEntityNotFoundException() {
        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> envelopeCategoryService.delete(categoryId));
    }

    // --- seedDefaultCategories ---

    @Test
    void seedDefaultCategories_createsAllDefaults() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(envelopeCategoryRepository.save(any(EnvelopeCategory.class))).thenAnswer(invocation -> {
            EnvelopeCategory saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setAppUser(appUser);
            return saved;
        });

        List<EnvelopeCategoryDTO> result = envelopeCategoryService.seedDefaultCategories(userId);

        assertEquals(5, result.size());
        verify(envelopeCategoryRepository, times(5)).save(any(EnvelopeCategory.class));
    }

    @Test
    void seedDefaultCategories_nullUserId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> envelopeCategoryService.seedDefaultCategories(null));
    }

    @Test
    void seedDefaultCategories_nonExistentUser_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> envelopeCategoryService.seedDefaultCategories(userId));
    }

    // --- update validation ---

    @Test
    void update_withBlankName_throwsIllegalArgument() {
        EnvelopeCategoryDTO dto = new EnvelopeCategoryDTO();
        dto.setName("  ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeCategoryService.update(categoryId, dto));
        assertEquals("Category name cannot be null or empty", ex.getMessage());
    }

    @Test
    void update_withNullName_throwsIllegalArgument() {
        EnvelopeCategoryDTO dto = new EnvelopeCategoryDTO();
        dto.setName(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeCategoryService.update(categoryId, dto));
        assertEquals("Category name cannot be null or empty", ex.getMessage());
    }

    // --- duplicate name ---

    @Test
    void create_withDuplicateName_throwsIllegalArgument() {
        EnvelopeCategory existingCategory = new EnvelopeCategory();
        existingCategory.setId(UUID.randomUUID());
        existingCategory.setName("Bills");
        when(envelopeCategoryRepository.findByAppUserIdAndName(userId, "Bills"))
                .thenReturn(List.of(existingCategory));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeCategoryService.create(categoryDTO));
        assertEquals("A category with this name already exists", ex.getMessage());
    }

    @Test
    void update_withDuplicateName_throwsIllegalArgument() {
        EnvelopeCategoryDTO dto = new EnvelopeCategoryDTO();
        dto.setName("Savings");

        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        EnvelopeCategory otherCategory = new EnvelopeCategory();
        otherCategory.setId(UUID.randomUUID());
        otherCategory.setName("Savings");
        when(envelopeCategoryRepository.findByAppUserIdAndName(userId, "Savings"))
                .thenReturn(List.of(otherCategory));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeCategoryService.update(categoryId, dto));
        assertEquals("A category with this name already exists", ex.getMessage());
    }

    @Test
    void update_withSameNameAsSelf_succeeds() {
        EnvelopeCategoryDTO dto = new EnvelopeCategoryDTO();
        dto.setName("Bills");

        when(envelopeCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(envelopeCategoryRepository.findByAppUserIdAndName(userId, "Bills"))
                .thenReturn(List.of(category));
        when(envelopeCategoryRepository.save(any(EnvelopeCategory.class))).thenReturn(category);

        EnvelopeCategoryDTO result = envelopeCategoryService.update(categoryId, dto);
        assertNotNull(result);
        assertEquals("Bills", result.getName());
    }
}
