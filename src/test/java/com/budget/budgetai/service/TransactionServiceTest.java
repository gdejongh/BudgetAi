package com.budget.budgetai.service;

import com.budget.budgetai.dto.CCPaymentRequest;
import com.budget.budgetai.dto.TransactionDTO;
import com.budget.budgetai.model.AccountType;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.BankAccount;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.model.Transaction;
import com.budget.budgetai.model.TransactionType;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
import com.budget.budgetai.repository.EnvelopeAllocationRepository;
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
import static org.mockito.Mockito.lenient;

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
    private EnvelopeAllocationRepository envelopeAllocationRepository;

    @Mock
    private BankAccountService bankAccountService;

    @Mock
    private EnvelopeAllocationService envelopeAllocationService;

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
                new BigDecimal("50.00"), "Grocery shopping", LocalDate.of(2026, 2, 20), null, null, null);

        // Default: all accounts are non-CC for existing tests
        lenient().when(bankAccountService.isCreditCard(any(UUID.class))).thenReturn(false);
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

        TransactionDTO result = transactionService.create(transactionDTO);

        assertNotNull(result);
        assertEquals(transactionId, result.getId());
        assertEquals(new BigDecimal("50.00"), result.getAmount());
        assertEquals("Grocery shopping", result.getDescription());
        assertEquals(userId, result.getAppUserId());
        assertEquals(bankAccountId, result.getBankAccountId());
        assertEquals(envelopeId, result.getEnvelopeId());
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("50.00"));
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

    // Add these tests to the // --- update --- section in
    // TransactionServiceTest.java

    @Test
    void update_sameAmountSameBankAccount_noBalanceUpdate() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("50.00"), "Same amount", LocalDate.of(2026, 2, 25), null, null, null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        transactionService.update(transactionId, updateDTO);

        verify(bankAccountService, never()).updateBalance(any(), any());
    }

    @Test
    void update_differentAmountSameBankAccount_appliesDifferenceToBalance() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("75.00"), "Updated", LocalDate.of(2026, 2, 25), null, null, null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("75.00"));
        updatedTx.setDescription("Updated");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        // difference = 75 - 50 = 25
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("25.00"));
    }

    @Test
    void update_differentBankAccount_reversesOldAndAppliesNewFullAmount() {
        UUID newBankAccountId = UUID.randomUUID();
        BankAccount newBankAccount = new BankAccount();
        newBankAccount.setId(newBankAccountId);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, newBankAccountId, envelopeId,
                new BigDecimal("75.00"), "Updated", LocalDate.of(2026, 2, 25), null, null, null);

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
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("-50.00"));
        verify(bankAccountService).updateBalance(newBankAccountId, new BigDecimal("75.00"));
    }

    @Test
    void update_envelopeRemoved_reversesOldEnvelopeBalance() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, null,
                new BigDecimal("50.00"), "No envelope", LocalDate.of(2026, 2, 25), null, null, null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(null);
        updatedTx.setAmount(new BigDecimal("50.00"));
        updatedTx.setDescription("No envelope");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        // No envelope balance adjustments expected
        verify(bankAccountService, never()).updateBalance(any(), any());
    }

    @Test
    void update_envelopeAdded_noBalanceAdjustment() {
        transaction.setEnvelope(null);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("50.00"), "With envelope", LocalDate.of(2026, 2, 25), null, null, null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("50.00"));
        updatedTx.setDescription("With envelope");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        // No envelope balance adjustments expected
        verify(bankAccountService, never()).updateBalance(any(), any());
    }

    @Test
    void update_differentEnvelope_noBalanceAdjustment() {
        UUID newEnvelopeId = UUID.randomUUID();
        Envelope newEnvelope = new Envelope();
        newEnvelope.setId(newEnvelopeId);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, newEnvelopeId,
                new BigDecimal("50.00"), "Changed envelope", LocalDate.of(2026, 2, 25), null, null, null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(newEnvelope);
        updatedTx.setAmount(new BigDecimal("50.00"));
        updatedTx.setDescription("Changed envelope");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(envelopeRepository.existsById(newEnvelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(newEnvelopeId)).thenReturn(newEnvelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        // No envelope balance adjustments expected; same account + same amount = no
        // bank change either
        verify(bankAccountService, never()).updateBalance(any(), any());
    }

    @Test
    void update_noBankAccountIdInDTO_appliesDifferenceToExistingBankAccount() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, null, envelopeId,
                new BigDecimal("80.00"), "No bank account id", LocalDate.of(2026, 2, 25), null, null, null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("80.00"));
        updatedTx.setDescription("No bank account id");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTx);

        transactionService.update(transactionId, updateDTO);

        // difference = 80 - 50 = 30
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("30.00"));
    }

    @Test
    void update_existingTransaction_updatesFields() {
        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, envelopeId,
                new BigDecimal("75.00"), "Updated description", LocalDate.of(2026, 2, 25), null, null, null);

        Transaction updatedTx = new Transaction();
        updatedTx.setId(transactionId);
        updatedTx.setAppUser(appUser);
        updatedTx.setBankAccount(bankAccount);
        updatedTx.setEnvelope(envelope);
        updatedTx.setAmount(new BigDecimal("75.00"));
        updatedTx.setDescription("Updated description");
        updatedTx.setTransactionDate(LocalDate.of(2026, 2, 25));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
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
                new BigDecimal("50.00"), "Transfer", LocalDate.of(2026, 2, 25), null, null, null);

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
                new BigDecimal("50.00"), "Test", LocalDate.of(2026, 2, 25), null, null, null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.existsById(invalidBankAccountId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.update(transactionId, updateDTO));
    }

    @Test
    void update_invalidEnvelope_throwsEntityNotFoundException() {
        UUID invalidEnvelopeId = UUID.randomUUID();
        TransactionDTO updateDTO = new TransactionDTO(null, userId, null, invalidEnvelopeId,
                new BigDecimal("50.00"), "Test", LocalDate.of(2026, 2, 25), null, null, null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(envelopeRepository.existsById(invalidEnvelopeId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> transactionService.update(transactionId, updateDTO));
    }

    // --- delete ---

    @Test
    void delete_existing_deletesSuccessfully() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        transactionService.delete(transactionId);

        verify(transactionRepository).delete(transaction);
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("-50.00"));
    }

    @Test
    void delete_nonExisting_throwsEntityNotFoundException() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> transactionService.delete(transactionId));
    }

    // --- Credit Card Tests ---

    @Test
    void create_creditCardPurchase_invertsBalanceUpdate() {
        UUID ccId = UUID.randomUUID();
        BankAccount creditCard = new BankAccount();
        creditCard.setId(ccId);
        creditCard.setAppUser(appUser);
        creditCard.setName("Visa");
        creditCard.setAccountType(AccountType.CREDIT_CARD);
        creditCard.setCurrentBalance(BigDecimal.ZERO);

        TransactionDTO ccDTO = new TransactionDTO(null, userId, ccId, envelopeId,
                new BigDecimal("-25.00"), "Grocery purchase", LocalDate.of(2026, 3, 1), null, null, null);

        Transaction ccTx = new Transaction();
        ccTx.setId(UUID.randomUUID());
        ccTx.setAppUser(appUser);
        ccTx.setBankAccount(creditCard);
        ccTx.setEnvelope(envelope);
        ccTx.setAmount(new BigDecimal("-25.00"));
        ccTx.setDescription("Grocery purchase");
        ccTx.setTransactionDate(LocalDate.of(2026, 3, 1));
        ccTx.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(ccId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(ccId)).thenReturn(creditCard);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(ccTx);
        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));

        transactionService.create(ccDTO);

        // CC purchase of -25 should invert to +25 on the CC balance (increases debt)
        verify(bankAccountService).updateBalance(ccId, new BigDecimal("25.00"));
    }

    @Test
    void delete_creditCardTransaction_invertsRevert() {
        UUID ccId = UUID.randomUUID();
        BankAccount creditCard = new BankAccount();
        creditCard.setId(ccId);
        creditCard.setAppUser(appUser);
        creditCard.setName("Visa");
        creditCard.setAccountType(AccountType.CREDIT_CARD);
        creditCard.setCurrentBalance(new BigDecimal("25.00"));

        Transaction ccTx = new Transaction();
        ccTx.setId(UUID.randomUUID());
        ccTx.setAppUser(appUser);
        ccTx.setBankAccount(creditCard);
        ccTx.setEnvelope(envelope);
        ccTx.setAmount(new BigDecimal("-25.00"));
        ccTx.setDescription("Grocery purchase");
        ccTx.setTransactionDate(LocalDate.of(2026, 3, 1));

        when(transactionRepository.findById(ccTx.getId())).thenReturn(Optional.of(ccTx));
        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));

        transactionService.delete(ccTx.getId());

        // Deleting a CC purchase of -25: revert = amount itself = -25 (reduces debt)
        verify(bankAccountService).updateBalance(ccId, new BigDecimal("-25.00"));
        verify(transactionRepository).delete(ccTx);
    }

    @Test
    void createCCPayment_happyPath_createsTwoLinkedTransactions() {
        UUID ccId = UUID.randomUUID();
        CCPaymentRequest request = new CCPaymentRequest(
                bankAccountId, ccId, new BigDecimal("100.00"),
                "CC Payment", LocalDate.of(2026, 3, 1));

        Transaction bankTxn = new Transaction();
        bankTxn.setId(UUID.randomUUID());
        bankTxn.setAppUser(appUser);
        bankTxn.setBankAccount(bankAccount);
        bankTxn.setAmount(new BigDecimal("-100.00"));
        bankTxn.setTransactionType(TransactionType.CC_PAYMENT);

        Transaction ccTxn = new Transaction();
        ccTxn.setId(UUID.randomUUID());
        ccTxn.setAppUser(appUser);
        ccTxn.setBankAccount(bankAccount);
        ccTxn.setAmount(new BigDecimal("100.00"));
        ccTxn.setTransactionType(TransactionType.CC_PAYMENT);

        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        when(bankAccountService.isCreditCard(bankAccountId)).thenReturn(false);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.getReferenceById(bankAccountId)).thenReturn(bankAccount);
        when(bankAccountRepository.getReferenceById(ccId)).thenReturn(bankAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(bankTxn)
                .thenReturn(ccTxn)
                .thenReturn(bankTxn)
                .thenReturn(ccTxn);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));

        TransactionDTO result = transactionService.createCCPayment(request, userId);

        assertNotNull(result);
        // 4 saves: bank txn, cc txn, bank txn (link), cc txn (link)
        verify(transactionRepository, times(4)).save(any(Transaction.class));
        // Bank account decreases by 100
        verify(bankAccountService).updateBalance(bankAccountId, new BigDecimal("-100.00"));
        // CC debt decreases by 100
        verify(bankAccountService).updateBalance(ccId, new BigDecimal("-100.00"));
    }

    @Test
    void createCCPayment_sourceIsCreditCard_throws() {
        UUID ccId = UUID.randomUUID();
        UUID anotherCcId = UUID.randomUUID();
        CCPaymentRequest request = new CCPaymentRequest(
                anotherCcId, ccId, new BigDecimal("50.00"),
                null, LocalDate.of(2026, 3, 1));

        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        when(bankAccountService.isCreditCard(anotherCcId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createCCPayment(request, userId));
    }

    @Test
    void createCCPayment_targetNotCreditCard_throws() {
        UUID notCcId = UUID.randomUUID();
        CCPaymentRequest request = new CCPaymentRequest(
                bankAccountId, notCcId, new BigDecimal("50.00"),
                null, LocalDate.of(2026, 3, 1));

        when(bankAccountService.isCreditCard(notCcId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createCCPayment(request, userId));
    }

    @Test
    void createCCPayment_nullRequest_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createCCPayment(null, userId));
    }

    @Test
    void createCCPayment_zeroAmount_throws() {
        UUID ccId = UUID.randomUUID();
        CCPaymentRequest request = new CCPaymentRequest(
                bankAccountId, ccId, BigDecimal.ZERO,
                null, LocalDate.of(2026, 3, 1));

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.createCCPayment(request, userId));
    }

    // --- Credit Card Auto-Move Tests ---

    @Test
    void create_ccPurchaseWithEnvelope_movesFullAmountToCCPaymentEnvelope() {
        UUID ccId = UUID.randomUUID();
        UUID ccPaymentEnvelopeId = UUID.randomUUID();

        BankAccount creditCard = new BankAccount();
        creditCard.setId(ccId);
        creditCard.setAppUser(appUser);
        creditCard.setName("Visa");
        creditCard.setAccountType(AccountType.CREDIT_CARD);
        creditCard.setCurrentBalance(BigDecimal.ZERO);

        Envelope ccPaymentEnvelope = new Envelope();
        ccPaymentEnvelope.setId(ccPaymentEnvelopeId);
        ccPaymentEnvelope.setAppUser(appUser);
        ccPaymentEnvelope.setName("Visa Payment");

        TransactionDTO ccDTO = new TransactionDTO(null, userId, ccId, envelopeId,
                new BigDecimal("-50.00"), "Grocery purchase", LocalDate.of(2026, 3, 1), null, null, null);

        Transaction ccTx = new Transaction();
        ccTx.setId(UUID.randomUUID());
        ccTx.setAppUser(appUser);
        ccTx.setBankAccount(creditCard);
        ccTx.setEnvelope(envelope);
        ccTx.setAmount(new BigDecimal("-50.00"));
        ccTx.setTransactionDate(LocalDate.of(2026, 3, 1));
        ccTx.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(ccId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(ccId)).thenReturn(creditCard);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(ccTx);
        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));
        when(envelopeRepository.findByLinkedAccountId(ccId)).thenReturn(Optional.of(ccPaymentEnvelope));

        transactionService.create(ccDTO);

        // Full purchase amount (50) should be added to CC Payment envelope, not capped
        // by remaining
        verify(envelopeAllocationService).addToAllocation(
                eq(ccPaymentEnvelopeId),
                eq(LocalDate.of(2026, 3, 1)),
                eq(new BigDecimal("50.00")));
    }

    @Test
    void create_ccPurchaseWithoutEnvelope_doesNotMoveToCCPaymentEnvelope() {
        UUID ccId = UUID.randomUUID();

        BankAccount creditCard = new BankAccount();
        creditCard.setId(ccId);
        creditCard.setAppUser(appUser);
        creditCard.setName("Visa");
        creditCard.setAccountType(AccountType.CREDIT_CARD);
        creditCard.setCurrentBalance(BigDecimal.ZERO);

        TransactionDTO ccDTO = new TransactionDTO(null, userId, ccId, null,
                new BigDecimal("-50.00"), "Unassigned purchase", LocalDate.of(2026, 3, 1), null, null, null);

        Transaction ccTx = new Transaction();
        ccTx.setId(UUID.randomUUID());
        ccTx.setAppUser(appUser);
        ccTx.setBankAccount(creditCard);
        ccTx.setEnvelope(null);
        ccTx.setAmount(new BigDecimal("-50.00"));
        ccTx.setTransactionDate(LocalDate.of(2026, 3, 1));
        ccTx.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(ccId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(ccId)).thenReturn(creditCard);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(ccTx);
        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));

        transactionService.create(ccDTO);

        // No auto-move should happen when no envelope is assigned
        verify(envelopeAllocationService, never()).addToAllocation(any(), any(), any());
    }

    @Test
    void create_ccRefundWithEnvelope_decrementsCCPaymentEnvelope() {
        UUID ccId = UUID.randomUUID();
        UUID ccPaymentEnvelopeId = UUID.randomUUID();

        BankAccount creditCard = new BankAccount();
        creditCard.setId(ccId);
        creditCard.setAppUser(appUser);
        creditCard.setName("Visa");
        creditCard.setAccountType(AccountType.CREDIT_CARD);
        creditCard.setCurrentBalance(new BigDecimal("100.00"));

        Envelope ccPaymentEnvelope = new Envelope();
        ccPaymentEnvelope.setId(ccPaymentEnvelopeId);
        ccPaymentEnvelope.setAppUser(appUser);
        ccPaymentEnvelope.setName("Visa Payment");

        TransactionDTO refundDTO = new TransactionDTO(null, userId, ccId, envelopeId,
                new BigDecimal("25.00"), "Refund", LocalDate.of(2026, 3, 1), null, null, null);

        Transaction refundTx = new Transaction();
        refundTx.setId(UUID.randomUUID());
        refundTx.setAppUser(appUser);
        refundTx.setBankAccount(creditCard);
        refundTx.setEnvelope(envelope);
        refundTx.setAmount(new BigDecimal("25.00"));
        refundTx.setTransactionDate(LocalDate.of(2026, 3, 1));
        refundTx.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(ccId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(ccId)).thenReturn(creditCard);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(envelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(refundTx);
        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));
        when(envelopeRepository.findByLinkedAccountId(ccId)).thenReturn(Optional.of(ccPaymentEnvelope));

        transactionService.create(refundDTO);

        // Refund of +25: CC Payment envelope should decrease by 25 (less cash needed)
        verify(envelopeAllocationService).addToAllocation(
                eq(ccPaymentEnvelopeId),
                eq(LocalDate.of(2026, 3, 1)),
                eq(new BigDecimal("-25.00")));
        // CC balance should decrease (refund reduces debt): +25 negated = -25
        verify(bankAccountService).updateBalance(ccId, new BigDecimal("-25.00"));
    }

    @Test
    void create_ccRefundWithoutEnvelope_doesNotChangeCCPaymentEnvelope() {
        UUID ccId = UUID.randomUUID();

        BankAccount creditCard = new BankAccount();
        creditCard.setId(ccId);
        creditCard.setAppUser(appUser);
        creditCard.setName("Visa");
        creditCard.setAccountType(AccountType.CREDIT_CARD);
        creditCard.setCurrentBalance(new BigDecimal("100.00"));

        TransactionDTO refundDTO = new TransactionDTO(null, userId, ccId, null,
                new BigDecimal("25.00"), "Unassigned refund", LocalDate.of(2026, 3, 1), null, null, null);

        Transaction refundTx = new Transaction();
        refundTx.setId(UUID.randomUUID());
        refundTx.setAppUser(appUser);
        refundTx.setBankAccount(creditCard);
        refundTx.setEnvelope(null);
        refundTx.setAmount(new BigDecimal("25.00"));
        refundTx.setTransactionDate(LocalDate.of(2026, 3, 1));
        refundTx.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(ccId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(ccId)).thenReturn(creditCard);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(refundTx);
        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));

        transactionService.create(refundDTO);

        // No CC Payment envelope adjustment for unassigned refunds
        verify(envelopeAllocationService, never()).addToAllocation(any(), any(), any());
    }

    @Test
    void create_overspentEnvelope_fullAmountStillMovedToCCPayment() {
        UUID ccId = UUID.randomUUID();
        UUID ccPaymentEnvelopeId = UUID.randomUUID();

        BankAccount creditCard = new BankAccount();
        creditCard.setId(ccId);
        creditCard.setAppUser(appUser);
        creditCard.setName("Visa");
        creditCard.setAccountType(AccountType.CREDIT_CARD);
        creditCard.setCurrentBalance(BigDecimal.ZERO);

        // Spending envelope with only $20 remaining
        Envelope spendingEnvelope = new Envelope();
        spendingEnvelope.setId(envelopeId);
        spendingEnvelope.setAppUser(appUser);
        spendingEnvelope.setName("Groceries");
        spendingEnvelope.setAllocatedBalance(new BigDecimal("20.00"));

        Envelope ccPaymentEnvelope = new Envelope();
        ccPaymentEnvelope.setId(ccPaymentEnvelopeId);
        ccPaymentEnvelope.setAppUser(appUser);
        ccPaymentEnvelope.setName("Visa Payment");

        // Purchase for $50 (exceeds envelope's $20 remaining)
        TransactionDTO ccDTO = new TransactionDTO(null, userId, ccId, envelopeId,
                new BigDecimal("-50.00"), "Big grocery purchase", LocalDate.of(2026, 3, 1), null, null, null);

        Transaction ccTx = new Transaction();
        ccTx.setId(UUID.randomUUID());
        ccTx.setAppUser(appUser);
        ccTx.setBankAccount(creditCard);
        ccTx.setEnvelope(spendingEnvelope);
        ccTx.setAmount(new BigDecimal("-50.00"));
        ccTx.setTransactionDate(LocalDate.of(2026, 3, 1));
        ccTx.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.existsById(ccId)).thenReturn(true);
        when(bankAccountRepository.getReferenceById(ccId)).thenReturn(creditCard);
        when(envelopeRepository.existsById(envelopeId)).thenReturn(true);
        when(envelopeRepository.getReferenceById(envelopeId)).thenReturn(spendingEnvelope);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(ccTx);
        when(bankAccountService.isCreditCard(ccId)).thenReturn(true);
        doNothing().when(bankAccountService).updateBalance(any(UUID.class), any(BigDecimal.class));
        when(envelopeRepository.findByLinkedAccountId(ccId)).thenReturn(Optional.of(ccPaymentEnvelope));

        transactionService.create(ccDTO);

        // Full $50 should be moved to CC Payment envelope even though envelope only had
        // $20
        verify(envelopeAllocationService).addToAllocation(
                eq(ccPaymentEnvelopeId),
                eq(LocalDate.of(2026, 3, 1)),
                eq(new BigDecimal("50.00")));
    }

    // --- IDOR: cross-user authorization ---

    @Test
    void create_withBankAccountBelongingToAnotherUser_throwsIllegalArgument() {
        UUID otherUserId = UUID.randomUUID();
        AppUser otherUser = new AppUser();
        otherUser.setId(otherUserId);

        BankAccount otherAccount = new BankAccount();
        otherAccount.setId(bankAccountId);
        otherAccount.setAppUser(otherUser);

        when(bankAccountRepository.findById(bankAccountId)).thenReturn(Optional.of(otherAccount));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.create(transactionDTO));
        assertEquals("Bank account does not belong to this user", ex.getMessage());
    }

    @Test
    void create_withEnvelopeBelongingToAnotherUser_throwsIllegalArgument() {
        UUID otherUserId = UUID.randomUUID();
        AppUser otherUser = new AppUser();
        otherUser.setId(otherUserId);

        Envelope otherEnvelope = new Envelope();
        otherEnvelope.setId(envelopeId);
        otherEnvelope.setAppUser(otherUser);

        when(bankAccountRepository.findById(bankAccountId)).thenReturn(Optional.of(bankAccount));
        when(envelopeRepository.findById(envelopeId)).thenReturn(Optional.of(otherEnvelope));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.create(transactionDTO));
        assertEquals("Envelope does not belong to this user", ex.getMessage());
    }

    @Test
    void update_withBankAccountBelongingToAnotherUser_throwsIllegalArgument() {
        UUID otherUserId = UUID.randomUUID();
        AppUser otherUser = new AppUser();
        otherUser.setId(otherUserId);

        UUID newAccountId = UUID.randomUUID();
        BankAccount otherAccount = new BankAccount();
        otherAccount.setId(newAccountId);
        otherAccount.setAppUser(otherUser);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, newAccountId, null,
                new BigDecimal("50.00"), "Test", LocalDate.of(2026, 2, 20), null, null, null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(bankAccountRepository.findById(newAccountId)).thenReturn(Optional.of(otherAccount));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.update(transactionId, updateDTO));
        assertEquals("Bank account does not belong to this user", ex.getMessage());
    }

    @Test
    void update_withEnvelopeBelongingToAnotherUser_throwsIllegalArgument() {
        UUID otherUserId = UUID.randomUUID();
        AppUser otherUser = new AppUser();
        otherUser.setId(otherUserId);

        UUID newEnvelopeId = UUID.randomUUID();
        Envelope otherEnvelope = new Envelope();
        otherEnvelope.setId(newEnvelopeId);
        otherEnvelope.setAppUser(otherUser);

        TransactionDTO updateDTO = new TransactionDTO(null, userId, bankAccountId, newEnvelopeId,
                new BigDecimal("50.00"), "Test", LocalDate.of(2026, 2, 20), null, null, null);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(envelopeRepository.findById(newEnvelopeId)).thenReturn(Optional.of(otherEnvelope));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.update(transactionId, updateDTO));
        assertEquals("Envelope does not belong to this user", ex.getMessage());
    }
}
