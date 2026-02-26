package com.budget.budgetai.service;

import com.budget.budgetai.dto.TransactionDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.BankAccount;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.model.Transaction;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private EnvelopeRepository envelopeRepository;

    @Mock
    private BankAccountService bankAccountService;

    @Mock
    private EnvelopeService envelopeService;

    @InjectMocks
    private TransactionService transactionService;

    private UUID transactionId;
    private UUID userId;
    private UUID bankAccountId;
    private UUID envelopeId;
    private AppUser appUser;
    private BankAccount bankAccount;
    private Envelope envelope;
    private Transaction transaction;
    private TransactionDTO transactionDTO;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        bankAccountId = UUID.randomUUID();
        envelopeId = UUID.randomUUID();

        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");

        bankAccount = new BankAccount();
        bankAccount.setId(bankAccountId);
        bankAccount.setAppUser(appUser);
        bankAccount.setName("Checking");
        bankAccount.setCurrentBalance(new BigDecimal("1000.00"));

        envelope = new Envelope();
        envelope.setId(envelopeId);
        envelope.setAppUser(appUser);
        envelope.setName("Groceries");
        envelope.setAllocatedBalance(new BigDecimal("500.00"));

        transaction = new Transaction();
        transaction.setId(transactionId);
        transaction.setAppUser(appUser);
        transaction.setBankAccount(bankAccount);
        transaction.setEnvelope(envelope);
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setDescription("Grocery shopping");
        transaction.setTransactionDate(LocalDate.of(2026, 2, 20));
        transaction.setCreatedAt(ZonedDateTime.now());

        transactionDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("50.00"), "Grocery shopping", LocalDate.of(2026, 2, 20), null);
    }

    // --- create ---

    @Test
    void create_happyPath_allFKsResolved() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));
        doNothing().when(envelopeService).updateBalance(any(UUID.class), any(BigDecimal.class));


        TransactionDTO result = transactionService.create(transactionDTO);

        assertNotNull(result);
        assertEquals(transactionId, result.getId());
        assertEquals(new BigDecimal("50.00"), result.getAmount());
        assertEquals("Grocery shopping", result.getDescription());
        assertEquals(userId, result.getAppUserId());
        assertEquals(bankAccountId, result.getBankAccountId());
        assertEquals(envelopeId, result.getEnvelopeId());
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("50.00"));
        verify(envelopeService).updateBalance(envelopeId, new BigDecimal("50.00"));
    }

    @Test
    void create_withoutEnvelope_succeeds() {
        transactionDTO.setEnvelopeId(null);
        Transaction txNoEnvelope = new Transaction();
        txNoEnvelope.setId(transactionId);
        txNoEnvelope.setAppUser(appUser);
        txNoEnvelope.setBankAccount(bankAccount);
        txNoEnvelope.setEnvelope(null);
        txNoEnvelope.setAmount(new BigDecimal("50.00"));
        txNoEnvelope.setDescription("Grocery shopping");
        txNoEnvelope.setTransactionDate(LocalDate.of(2026, 2, 20));

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txNoEnvelope);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));

        TransactionDTO result = transactionService.create(transactionDTO);

        assertNotNull(result);
        assertNull(result.getEnvelopeId());
    }

    @Test
    void create_missingUser_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.create(transactionDTO));
    }

    @Test
    void create_missingBankAccount_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.create(transactionDTO));
    }

    @Test
    void create_missingEnvelope_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.create(transactionDTO));
    }

    @Test
    void create_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.create(null));
    }

    @Test
    void create_nullAmount_throwsIllegalArgumentException() {
        transactionDTO.setAmount(null);
        assertThrows(IllegalArgumentException.class, () -> transactionService.create(transactionDTO));
    }

    @Test
    void create_nullTransactionDate_throwsIllegalArgumentException() {
        transactionDTO.setTransactionDate(null);
        assertThrows(IllegalArgumentException.class, () -> transactionService.create(transactionDTO));
    }

    @Test
    void create_nullAppUserId_throwsIllegalArgumentException() {
        transactionDTO.setAppUserId(null);
        assertThrows(IllegalArgumentException.class, () -> transactionService.create(transactionDTO));
    }

    @Test
    void create_nullBankAccountId_throwsIllegalArgumentException() {
        transactionDTO.setBankAccountId(null);
        assertThrows(IllegalArgumentException.class, () -> transactionService.create(transactionDTO));
    }

    // --- getById ---

    @Test
    void getById_found_returnsDTO() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        TransactionDTO result = transactionService.getById(transactionId);

        assertNotNull(result);
        assertEquals(transactionId, result.getId());
        assertEquals(new BigDecimal("50.00"), result.getAmount());
    }

    @Test
    void getById_notFound_throwsEntityNotFoundException() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> transactionService.getById(transactionId));
    }

    // --- getAll ---

    @Test
    void getAll_returnsAll() {
        when(transactionRepository.findAll()).thenReturn(List.of(transaction));

        List<TransactionDTO> result = transactionService.getAll();

        assertEquals(1, result.size());
    }

    @Test
    void getAll_empty_returnsEmptyList() {
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());

        List<TransactionDTO> result = transactionService.getAll();

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserId ---

    @Test
    void getByAppUserId_withResults_returnsDTOs() {
        when(transactionRepository.findByAppUserId(userId)).thenReturn(List.of(transaction));

        List<TransactionDTO> result = transactionService.getByAppUserId(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getAppUserId());
    }

    @Test
    void getByAppUserId_empty_returnsEmptyList() {
        when(transactionRepository.findByAppUserId(userId)).thenReturn(Collections.emptyList());

        List<TransactionDTO> result = transactionService.getByAppUserId(userId);

        assertTrue(result.isEmpty());
    }

    // --- getByBankAccountId ---

    @Test
    void getByBankAccountId_withResults_returnsDTOs() {
        when(transactionRepository.findByBankAccountId(bankAccountId)).thenReturn(List.of(transaction));

        List<TransactionDTO> result = transactionService.getByBankAccountId(bankAccountId);

        assertEquals(1, result.size());
        assertEquals(bankAccountId, result.get(0).getBankAccountId());
    }

    @Test
    void getByBankAccountId_empty_returnsEmptyList() {
        when(transactionRepository.findByBankAccountId(bankAccountId)).thenReturn(Collections.emptyList());

        List<TransactionDTO> result = transactionService.getByBankAccountId(bankAccountId);

        assertTrue(result.isEmpty());
    }

    // --- getByEnvelopeId ---

    @Test
    void getByEnvelopeId_withResults_returnsDTOs() {
        when(transactionRepository.findByEnvelopeId(envelopeId)).thenReturn(List.of(transaction));

        List<TransactionDTO> result = transactionService.getByEnvelopeId(envelopeId);

        assertEquals(1, result.size());
        assertEquals(envelopeId, result.get(0).getEnvelopeId());
    }

    @Test
    void getByEnvelopeId_empty_returnsEmptyList() {
        when(transactionRepository.findByEnvelopeId(envelopeId)).thenReturn(Collections.emptyList());

        List<TransactionDTO> result = transactionService.getByEnvelopeId(envelopeId);

        assertTrue(result.isEmpty());
    }

    // --- getByTransactionDateBetween ---

    @Test
    void getByTransactionDateBetween_withResults_returnsDTOs() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        when(transactionRepository.findByTransactionDateBetween(start, end)).thenReturn(List.of(transaction));

        List<TransactionDTO> result = transactionService.getByTransactionDateBetween(start, end);

        assertEquals(1, result.size());
    }

    @Test
    void getByTransactionDateBetween_empty_returnsEmptyList() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);

        when(transactionRepository.findByTransactionDateBetween(start, end)).thenReturn(Collections.emptyList());

        List<TransactionDTO> result = transactionService.getByTransactionDateBetween(start, end);

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserIdAndTransactionDateBetween ---

    @Test
    void getByAppUserIdAndTransactionDateBetween_withResults_returnsDTOs() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        when(transactionRepository.findByAppUserIdAndTransactionDateBetween(userId, start, end))
                .thenReturn(List.of(transaction));

        List<TransactionDTO> result = transactionService.getByAppUserIdAndTransactionDateBetween(userId, start, end);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getAppUserId());
    }

    @Test
    void getByAppUserIdAndTransactionDateBetween_empty_returnsEmptyList() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);

        when(transactionRepository.findByAppUserIdAndTransactionDateBetween(userId, start, end))
                .thenReturn(Collections.emptyList());

        List<TransactionDTO> result = transactionService.getByAppUserIdAndTransactionDateBetween(userId, start, end);

        assertTrue(result.isEmpty());
    }

    // --- update ---

// Add these tests to the // --- update --- section in TransactionServiceTest.java

    @Test
    void update_sameAmountSameBankAccount_noBalanceUpdate() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("50.00"), "Same amount", LocalDate.of(2026, 2, 25), null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        transactionService.update(transactionId, updateDTO);

        verify(bankAccountService, never()).updateBalance(any(), any());
        verify(envelopeService, never()).updateBalance(any(), any());
    }

    @Test
    void update_differentAmountSameBankAccount_appliesDifferenceToBalance() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("75.00"), "Updated", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("75.00"));
        updatedTx.setDescription("Updated");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        // difference = 75 - 50 = 25
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("25.00"));
        verify(envelopeService).updateBalance(envelopeId, new BigDecimal("25.00"));
    }

    @Test
    void update_differentBankAccount_reversesOldAndAppliesNewFullAmount() {
        UUID newBankAccountId = UUID.randomUUID();
        BankAccount newBankAccount = new BankAccount();
        newBankAccount.setId(newBankAccountId);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, newBankAccountId, envelopeId,
                new BigDecimal("75.00"), "Updated", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(newBankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("75.00"));
        updatedTx.setDescription("Updated");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(newBankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(newBankAccountId)).thenReturn(newBankAccount);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("-50.00"));
        verify(bankAccountService).updateBalance(newBankAccountId, new BigDecimal("75.00"));
    }

    @Test
    void update_envelopeRemoved_reversesOldEnvelopeBalance() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, null,
                new BigDecimal("50.00"), "No envelope", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(null);
        updatedTx.setAmount(new BigDecimal("50.00"));
        updatedTx.setDescription("No envelope");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        verify(envelopeService).updateBalance(envelopeId, new BigDecimal("-50.00"));
    }

    @Test
    void update_envelopeAdded_appliesNewAmountToNewEnvelope() {
        transaction.setEnvelope(null);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("50.00"), "With envelope", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("50.00"));
        updatedTx.setDescription("With envelope");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        verify(envelopeService).updateBalance(envelopeId, new BigDecimal("50.00"));
    }

    @Test
    void update_differentEnvelope_reversesOldAndAppliesNewFullAmount() {
        UUID newEnvelopeId = UUID.randomUUID();
        Envelope newEnvelope = new Envelope();
        newEnvelope.setId(newEnvelopeId);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, newEnvelopeId,
                new BigDecimal("75.00"), "Changed envelope", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(newEnvelope);
        updatedTx.setAmount(new BigDecimal("75.00"));
        updatedTx.setDescription("Changed envelope");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(envelopeRepository.existsById(newEnvelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(newEnvelopeId)).thenReturn(newEnvelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        verify(envelopeService).updateBalance(envelopeId, new BigDecimal("-50.00"));
        verify(envelopeService).updateBalance(newEnvelopeId, new BigDecimal("75.00"));
    }

    @Test
    void update_noBankAccountIdInDTO_appliesDifferenceToExistingBankAccount() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, null, envelopeId,
                new BigDecimal("80.00"), "No bank account id", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("80.00"));
        updatedTx.setDescription("No bank account id");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        // difference = 80 - 50 = 30
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("30.00"));
    }


    @Test
    void update_existingTransaction_updatesFields() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("75.00"), "Updated description", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("75.00"));
        updatedTx.setDescription("Updated description");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(bankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        TransactionDTO result = transactionService.update(transactionId, updateDTO);

        assertNotNull(result);
        assertEquals(new BigDecimal("75.00"), result.getAmount());
        assertEquals("Updated description", result.getDescription());
        assertEquals(LocalDate.of(2026, 2, 25), result.getTransactionDate());
    }

    @Test
    void update_nonExisting_throwsEntityNotFoundException() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> transactionService.update(transactionId, transactionDTO));
    }

    @Test
    void update_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> transactionService.update(transactionId, null));
    }

    @Test
    void update_changesBankAccount_resolvesNewFK() {
        UUID newBankAccountId = UUID.randomUUID();
        BankAccount newBankAccount = new BankAccount();
        newBankAccount.setId(newBankAccountId);
        newBankAccount.setAppUser(appUser);
        newBankAccount.setName("Savings");

        TransactionDTO updateDTO = new TransactionDTO(null, userId, newBankAccountId, null,
                new BigDecimal("50.00"), "Transfer", LocalDate.of(2026, 2, 25), null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(newBankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("50.00"));
        updatedTx.setDescription("Transfer");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(newBankAccountId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(newBankAccountId)).thenReturn(newBankAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        TransactionDTO result = transactionService.update(transactionId, updateDTO);

        assertNotNull(result);
        assertEquals(newBankAccountId, result.getBankAccountId());
        verify(bankAccountRepository).getReferenceById(newBankAccountId);
    }

    @Test
    void update_invalidBankAccount_throwsEntityNotFoundException() {
        UUID invalidBankAccountId = UUID.randomUUID();
        TransactionDTO updateDTO = new TransactionDTO(null, userId, invalidBankAccountId, null,
                new BigDecimal("50.00"), "Test", LocalDate.of(2026, 2, 25), null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(invalidBankAccountId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.update(transactionId, updateDTO));
    }

    @Test
    void update_invalidEnvelope_throwsEntityNotFoundException() {
        UUID invalidEnvelopeId = UUID.randomUUID();
        TransactionDTO updateDTO = new TransactionDTO(null, userId, null, invalidEnvelopeId,
                new BigDecimal("50.00"), "Test", LocalDate.of(2026, 2, 25), null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(envelopeRepository.existsById(invalidEnvelopeId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.update(transactionId, updateDTO));
    }

    // --- delete ---

    @Test
    void delete_existing_deletesSuccessfully() {
        when(transactionRepository.existsById(transactionId)).thenReturn(true);

        transactionService.delete(transactionId);

        verify(transactionRepository).deleteById(transactionId);
    }

    @Test
    void delete_nonExisting_throwsEntityNotFoundException() {
        when(transactionRepository.existsById(transactionId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.delete(transactionId));
    }
}
