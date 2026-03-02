package com.budget.budgetai.service;

import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.dto.EnvelopeSpentSummaryDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.model.EnvelopeCategory;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
import com.budget.budgetai.repository.EnvelopeAllocationRepository;
import com.budget.budgetai.repository.EnvelopeCategoryRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import com.budget.budgetai.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.eq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvelopeServiceTest {

    @Mock
    private EnvelopeRepository envelopeRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private EnvelopeCategoryRepository envelopeCategoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EnvelopeAllocationRepository envelopeAllocationRepository;

    @Mock
    private EnvelopeAllocationService envelopeAllocationService;

    @InjectMocks
    private EnvelopeService envelopeService;

    private UUID envelopeId;
    private UUID userId;
    private UUID categoryId;
    private AppUser appUser;
    private EnvelopeCategory envelopeCategory;
    private Envelope envelope;
    private EnvelopeDTO envelopeDTO;

    @BeforeEach
    void setUp() {
        envelopeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");

        envelopeCategory = new EnvelopeCategory();
        envelopeCategory.setId(categoryId);
        envelopeCategory.setAppUser(appUser);
        envelopeCategory.setName("Bills");

        envelope = new Envelope();
        envelope.setId(envelopeId);
        envelope.setAppUser(appUser);
        envelope.setEnvelopeCategory(envelopeCategory);
        envelope.setName("Groceries");
        envelope.setAllocatedBalance(new BigDecimal("500.00"));
        envelope.setCreatedAt(ZonedDateTime.now());

        envelopeDTO = new EnvelopeDTO(null, userId, categoryId, "Groceries", new BigDecimal("500.00"), null);
    }

    // --- create ---

    @Test
    void create_happyPath_returnsSavedDTO() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(envelopeCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(envelopeCategoryRepository.getReferenceById(categoryId)).thenReturn(envelopeCategory);
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(envelope);
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("500.00"));

        EnvelopeDTO result = envelopeService.create(envelopeDTO);

        assertNotNull(result);
        assertEquals(envelopeId, result.getId());
        assertEquals("Groceries", result.getName());
        assertEquals(new BigDecimal("500.00"), result.getAllocatedBalance());
        assertEquals(userId, result.getAppUserId());
        verify(envelopeAllocationService).createInitialAllocation(any(Envelope.class), eq(new BigDecimal("500.00")));
    }

    @Test
    void create_nonExistentUser_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> envelopeService.create(envelopeDTO));
    }

    @Test
    void create_nullCategoryId_throwsIllegalArgumentException() {
        envelopeDTO.setEnvelopeCategoryId(null);
        assertThrows(IllegalArgumentException.class, () -> envelopeService.create(envelopeDTO));
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
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("500.00"));

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
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("500.00"));

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
        List<Object[]> allocRows = new ArrayList<>();
        allocRows.add(new Object[] { envelopeId, new BigDecimal("500.00") });
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeForUser(userId))
                .thenReturn(allocRows);

        List<EnvelopeDTO> result = envelopeService.getByAppUserId(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getAppUserId());
    }

    @Test
    void getByAppUserId_empty_returnsEmptyList() {
        when(envelopeRepository.findByAppUserId(userId)).thenReturn(Collections.emptyList());
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeForUser(userId))
                .thenReturn(Collections.emptyList());

        List<EnvelopeDTO> result = envelopeService.getByAppUserId(userId);

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserIdAndName ---

    @Test
    void getByAppUserIdAndName_withResults_returnsDTOs() {
        when(envelopeRepository.findByAppUserIdAndName(userId, "Groceries")).thenReturn(List.of(envelope));
        List<Object[]> allocRows = new ArrayList<>();
        allocRows.add(new Object[] { envelopeId, new BigDecimal("500.00") });
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeForUser(userId))
                .thenReturn(allocRows);

        List<EnvelopeDTO> result = envelopeService.getByAppUserIdAndName(userId, "Groceries");

        assertEquals(1, result.size());
        assertEquals("Groceries", result.get(0).getName());
    }

    @Test
    void getByAppUserIdAndName_empty_returnsEmptyList() {
        when(envelopeRepository.findByAppUserIdAndName(userId, "NonExistent")).thenReturn(Collections.emptyList());
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeForUser(userId))
                .thenReturn(Collections.emptyList());

        List<EnvelopeDTO> result = envelopeService.getByAppUserIdAndName(userId, "NonExistent");

        assertTrue(result.isEmpty());
    }

    // --- update ---

    @Test
    void update_existing_updatesNameAndBalance() {
        EnvelopeDTO updateDTO = new EnvelopeDTO(null, userId, categoryId, "Entertainment", new BigDecimal("200.00"),
                null);
        Envelope updatedEnvelope = new Envelope();
        updatedEnvelope.setId(envelopeId);
        updatedEnvelope.setAppUser(appUser);
        updatedEnvelope.setEnvelopeCategory(envelopeCategory);
        updatedEnvelope.setName("Entertainment");
        updatedEnvelope.setAllocatedBalance(new BigDecimal("200.00"));

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(envelopeCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(envelopeCategoryRepository.getReferenceById(categoryId)).thenReturn(envelopeCategory);
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(updatedEnvelope);
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("200.00"));

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
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));

        envelopeService.delete(envelopeId);

        verify(envelopeRepository).deleteById(envelopeId);
    }

    @Test
    void delete_nonExisting_throwsEntityNotFoundException() {
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> envelopeService.delete(envelopeId));
    }

    // --- getSpentSummaries ---

    @Test
    void getSpentSummaries_returnsAllTimeAndPeriodSpent() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);
        UUID envelope1Id = UUID.randomUUID();
        UUID envelope2Id = UUID.randomUUID();

        List<Object[]> allTimeRows = Arrays.asList(
                new Object[] { envelope1Id, new BigDecimal("200.00") },
                new Object[] { envelope2Id, new BigDecimal("350.00") });
        List<Object[]> periodRows = Collections.singletonList(
                new Object[] { envelope1Id, new BigDecimal("100.00") });

        when(transactionRepository.sumAmountByEnvelopeForUser(userId)).thenReturn(allTimeRows);
        when(transactionRepository.sumAmountByEnvelopeForUserInDateRange(userId, start, end)).thenReturn(periodRows);

        List<EnvelopeSpentSummaryDTO> result = envelopeService.getSpentSummaries(userId, start, end);

        assertEquals(2, result.size());

        EnvelopeSpentSummaryDTO summary1 = result.stream()
                .filter(s -> s.getEnvelopeId().equals(envelope1Id)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("200.00"), summary1.getTotalSpent());
        assertEquals(new BigDecimal("100.00"), summary1.getPeriodSpent());

        EnvelopeSpentSummaryDTO summary2 = result.stream()
                .filter(s -> s.getEnvelopeId().equals(envelope2Id)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("350.00"), summary2.getTotalSpent());
        assertEquals(BigDecimal.ZERO, summary2.getPeriodSpent());
    }

    @Test
    void getSpentSummaries_noTransactions_returnsEmptyList() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        when(transactionRepository.sumAmountByEnvelopeForUser(userId)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumAmountByEnvelopeForUserInDateRange(userId, start, end))
                .thenReturn(Collections.emptyList());

        List<EnvelopeSpentSummaryDTO> result = envelopeService.getSpentSummaries(userId, start, end);

        assertTrue(result.isEmpty());
    }

    // --- savings goal ---

    @Test
    void update_withMonthlyGoal_persistsGoalFields() {
        EnvelopeDTO updateDTO = new EnvelopeDTO(null, userId, categoryId, "Groceries", new BigDecimal("0.00"),
                null, null, null, new BigDecimal("200.00"),
                null, "MONTHLY", null);

        Envelope updatedEnvelope = new Envelope();
        updatedEnvelope.setId(envelopeId);
        updatedEnvelope.setAppUser(appUser);
        updatedEnvelope.setEnvelopeCategory(envelopeCategory);
        updatedEnvelope.setName("Groceries");
        updatedEnvelope.setAllocatedBalance(new BigDecimal("0.00"));
        updatedEnvelope.setMonthlyGoalTarget(new BigDecimal("200.00"));
        updatedEnvelope.setGoalType("MONTHLY");

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(envelopeCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(envelopeCategoryRepository.getReferenceById(categoryId)).thenReturn(envelopeCategory);
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(updatedEnvelope);
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("0.00"));

        EnvelopeDTO result = envelopeService.update(envelopeId, updateDTO);

        assertNotNull(result);
        assertEquals("MONTHLY", result.getGoalType());
        assertEquals(new BigDecimal("200.00"), result.getMonthlyGoalTarget());
        assertNull(result.getGoalAmount());
        assertNull(result.getGoalTargetDate());
    }

    @Test
    void update_withTargetGoal_persistsGoalFields() {
        EnvelopeDTO updateDTO = new EnvelopeDTO(null, userId, categoryId, "New Car", new BigDecimal("0.00"),
                null, null, new BigDecimal("10000.00"), null,
                LocalDate.of(2030, 1, 1), "TARGET", null);

        Envelope updatedEnvelope = new Envelope();
        updatedEnvelope.setId(envelopeId);
        updatedEnvelope.setAppUser(appUser);
        updatedEnvelope.setEnvelopeCategory(envelopeCategory);
        updatedEnvelope.setName("New Car");
        updatedEnvelope.setAllocatedBalance(new BigDecimal("0.00"));
        updatedEnvelope.setGoalAmount(new BigDecimal("10000.00"));
        updatedEnvelope.setGoalTargetDate(LocalDate.of(2030, 1, 1));
        updatedEnvelope.setGoalType("TARGET");

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(envelopeCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(envelopeCategoryRepository.getReferenceById(categoryId)).thenReturn(envelopeCategory);
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(updatedEnvelope);
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("0.00"));

        EnvelopeDTO result = envelopeService.update(envelopeId, updateDTO);

        assertNotNull(result);
        assertEquals("TARGET", result.getGoalType());
        assertEquals(new BigDecimal("10000.00"), result.getGoalAmount());
        assertEquals(LocalDate.of(2030, 1, 1), result.getGoalTargetDate());
        assertNull(result.getMonthlyGoalTarget());
    }

    @Test
    void update_clearSavingsGoal_setsGoalFieldsToNull() {
        // Start with goal fields set
        envelope.setGoalAmount(new BigDecimal("10000.00"));
        envelope.setMonthlyGoalTarget(new BigDecimal("200.00"));
        envelope.setGoalTargetDate(LocalDate.of(2030, 1, 1));
        envelope.setGoalType("TARGET");

        EnvelopeDTO updateDTO = new EnvelopeDTO(null, userId, categoryId, "New Car", new BigDecimal("0.00"),
                null, null, null, null, null, null, null);

        Envelope updatedEnvelope = new Envelope();
        updatedEnvelope.setId(envelopeId);
        updatedEnvelope.setAppUser(appUser);
        updatedEnvelope.setEnvelopeCategory(envelopeCategory);
        updatedEnvelope.setName("New Car");
        updatedEnvelope.setAllocatedBalance(new BigDecimal("0.00"));

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(envelopeCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(envelopeCategoryRepository.getReferenceById(categoryId)).thenReturn(envelopeCategory);
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(updatedEnvelope);
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("0.00"));

        EnvelopeDTO result = envelopeService.update(envelopeId, updateDTO);

        assertNotNull(result);
        assertNull(result.getGoalType());
        assertNull(result.getGoalAmount());
        assertNull(result.getMonthlyGoalTarget());
        assertNull(result.getGoalTargetDate());
    }

    @Test
    void create_withTargetGoal_returnsDTOWithGoalFields() {
        EnvelopeDTO createDTO = new EnvelopeDTO(null, userId, categoryId, "Vacation", new BigDecimal("100.00"),
                null, null, new BigDecimal("5000.00"), null,
                LocalDate.of(2028, 6, 1), "TARGET", null);

        Envelope savedEnvelope = new Envelope();
        savedEnvelope.setId(envelopeId);
        savedEnvelope.setAppUser(appUser);
        savedEnvelope.setEnvelopeCategory(envelopeCategory);
        savedEnvelope.setName("Vacation");
        savedEnvelope.setAllocatedBalance(new BigDecimal("100.00"));
        savedEnvelope.setGoalAmount(new BigDecimal("5000.00"));
        savedEnvelope.setGoalTargetDate(LocalDate.of(2028, 6, 1));
        savedEnvelope.setGoalType("TARGET");

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(envelopeCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(envelopeCategoryRepository.getReferenceById(categoryId)).thenReturn(envelopeCategory);
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(savedEnvelope);
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("100.00"));

        EnvelopeDTO result = envelopeService.create(createDTO);

        assertNotNull(result);
        assertEquals("TARGET", result.getGoalType());
        assertEquals(new BigDecimal("5000.00"), result.getGoalAmount());
        assertEquals(LocalDate.of(2028, 6, 1), result.getGoalTargetDate());
    }

    @Test
    void getById_withSavingsGoal_returnsGoalFields() {
        envelope.setGoalAmount(new BigDecimal("10000.00"));
        envelope.setGoalTargetDate(LocalDate.of(2030, 1, 1));
        envelope.setGoalType("TARGET");

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId)).thenReturn(new BigDecimal("500.00"));

        EnvelopeDTO result = envelopeService.getById(envelopeId);

        assertNotNull(result);
        assertEquals("TARGET", result.getGoalType());
        assertEquals(new BigDecimal("10000.00"), result.getGoalAmount());
        assertEquals(LocalDate.of(2030, 1, 1), result.getGoalTargetDate());
    }

    // --- update validation ---

    @Test
    void update_withBlankName_throwsIllegalArgument() {
        EnvelopeDTO dto = new EnvelopeDTO();
        dto.setName("  ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeService.update(envelopeId, dto));
        assertEquals("Envelope name cannot be null or empty", ex.getMessage());
    }

    @Test
    void update_withNullName_throwsIllegalArgument() {
        EnvelopeDTO dto = new EnvelopeDTO();
        dto.setName(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeService.update(envelopeId, dto));
        assertEquals("Envelope name cannot be null or empty", ex.getMessage());
    }

    @Test
    void update_withNegativeGoalAmount_throwsIllegalArgument() {
        EnvelopeDTO dto = new EnvelopeDTO();
        dto.setName("Valid");
        dto.setGoalAmount(new BigDecimal("-100.00"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeService.update(envelopeId, dto));
        assertEquals("Goal amount must be greater than zero", ex.getMessage());
    }

    @Test
    void update_withZeroMonthlyGoalTarget_throwsIllegalArgument() {
        EnvelopeDTO dto = new EnvelopeDTO();
        dto.setName("Valid");
        dto.setMonthlyGoalTarget(BigDecimal.ZERO);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeService.update(envelopeId, dto));
        assertEquals("Monthly goal target must be greater than zero", ex.getMessage());
    }

    // --- duplicate name ---

    @Test
    void create_withDuplicateName_throwsIllegalArgument() {
        Envelope existingEnvelope = new Envelope();
        existingEnvelope.setId(UUID.randomUUID());
        existingEnvelope.setName("Groceries");
        when(envelopeRepository.findByAppUserIdAndName(userId, "Groceries"))
                .thenReturn(List.of(existingEnvelope));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeService.create(envelopeDTO));
        assertEquals("An envelope with this name already exists", ex.getMessage());
    }

    @Test
    void update_withDuplicateName_throwsIllegalArgument() {
        EnvelopeDTO dto = new EnvelopeDTO();
        dto.setName("Rent");

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));

        Envelope otherEnvelope = new Envelope();
        otherEnvelope.setId(UUID.randomUUID());
        otherEnvelope.setName("Rent");
        when(envelopeRepository.findByAppUserIdAndName(userId, "Rent"))
                .thenReturn(List.of(otherEnvelope));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> envelopeService.update(envelopeId, dto));
        assertEquals("An envelope with this name already exists", ex.getMessage());
    }

    @Test
    void update_withSameNameAsSelf_succeeds() {
        EnvelopeDTO dto = new EnvelopeDTO();
        dto.setName("Groceries");

        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(envelope));
        when(envelopeRepository.findByAppUserIdAndName(userId, "Groceries"))
                .thenReturn(List.of(envelope));
        when(envelopeRepository.save(any(Envelope.class))).thenReturn(envelope);
        when(envelopeAllocationRepository.sumAllocationsByEnvelopeId(envelopeId))
                .thenReturn(new BigDecimal("500.00"));

        EnvelopeDTO result = envelopeService.update(envelopeId, dto);
        assertNotNull(result);
        assertEquals("Groceries", result.getName());
    }
}
