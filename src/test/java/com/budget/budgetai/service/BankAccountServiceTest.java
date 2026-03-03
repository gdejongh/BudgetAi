package com.budget.budgetai.service;

import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.model.AccountType;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.BankAccount;
import com.budget.budgetai.model.Transaction;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
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
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EnvelopeCategoryRepository envelopeCategoryRepository;

    @Mock
    private EnvelopeRepository envelopeRepository;

    @InjectMocks
    private BankAccountService bankAccountService;

    private UUID accountId;
    private UUID userId;
    private AppUser appUser;
    private BankAccount bankAccount;
    private BankAccountDTO bankAccountDTO;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        userId = UUID.randomUUID();

        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");

        bankAccount = new BankAccount();
        bankAccount.setId(accountId);
        bankAccount.setAppUser(appUser);
        bankAccount.setName("Checking");
        bankAccount.setCurrentBalance(new BigDecimal("1000.00"));
        bankAccount.setCreatedAt(ZonedDateTime.now());

        bankAccountDTO = new BankAccountDTO(null, userId, "Checking", "CHECKING", new BigDecimal("1000.00"), null);
    }

    // --- create ---

    @Test
    void create_happyPath_returnsSavedDTO() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(bankAccount);

        BankAccountDTO result = bankAccountService.create(bankAccountDTO);

        assertNotNull(result);
        assertEquals(accountId, result.getId());
        assertEquals("Checking", result.getName());
        assertEquals(new BigDecimal("1000.00"), result.getCurrentBalance());
        assertEquals(userId, result.getAppUserId());
    }

    @Test
    void create_nonZeroBalance_createsInitialBalanceTransaction() {
        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(bankAccount);

        bankAccountService.create(bankAccountDTO);

        verify(transactionRepository).save(argThat(txn -> txn.getAmount().compareTo(new BigDecimal("1000.00")) == 0
                && "Initial Balance".equals(txn.getDescription())
                && txn.getBankAccount().getId().equals(accountId)
                && txn.getAppUser().getId().equals(userId)
                && txn.getEnvelope() == null));
    }

    @Test
    void create_zeroBalance_doesNotCreateTransaction() {
        BankAccountDTO zeroDTO = new BankAccountDTO(null, userId, "Checking", "CHECKING", BigDecimal.ZERO, null);
        BankAccount zeroAccount = new BankAccount();
        zeroAccount.setId(accountId);
        zeroAccount.setAppUser(appUser);
        zeroAccount.setName("Checking");
        zeroAccount.setCurrentBalance(BigDecimal.ZERO);

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(zeroAccount);

        bankAccountService.create(zeroDTO);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void create_nonExistentUser_throwsEntityNotFoundException() {
        when(appUserRepository.existsById(userId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> bankAccountService.create(bankAccountDTO));
    }

    @Test
    void create_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.create(null));
    }

    @Test
    void create_nullName_throwsIllegalArgumentException() {
        bankAccountDTO.setName(null);
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.create(bankAccountDTO));
    }

    @Test
    void create_nullBalance_throwsIllegalArgumentException() {
        bankAccountDTO.setCurrentBalance(null);
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.create(bankAccountDTO));
    }

    @Test
    void create_nullAppUserId_throwsIllegalArgumentException() {
        bankAccountDTO.setAppUserId(null);
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.create(bankAccountDTO));
    }

    // --- getById ---

    @Test
    void getById_found_returnsDTO() {
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));

        BankAccountDTO result = bankAccountService.getById(accountId);

        assertNotNull(result);
        assertEquals(accountId, result.getId());
        assertEquals("Checking", result.getName());
    }

    @Test
    void getById_notFound_throwsEntityNotFoundException() {
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bankAccountService.getById(accountId));
    }

    // --- getAll ---

    @Test
    void getAll_returnsAll() {
        when(bankAccountRepository.findAll()).thenReturn(List.of(bankAccount));

        List<BankAccountDTO> result = bankAccountService.getAll();

        assertEquals(1, result.size());
        assertEquals("Checking", result.get(0).getName());
    }

    @Test
    void getAll_empty_returnsEmptyList() {
        when(bankAccountRepository.findAll()).thenReturn(Collections.emptyList());

        List<BankAccountDTO> result = bankAccountService.getAll();

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserId ---

    @Test
    void getByAppUserId_withResults_returnsDTOs() {
        when(bankAccountRepository.findByAppUserId(userId)).thenReturn(List.of(bankAccount));

        List<BankAccountDTO> result = bankAccountService.getByAppUserId(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getAppUserId());
    }

    @Test
    void getByAppUserId_empty_returnsEmptyList() {
        when(bankAccountRepository.findByAppUserId(userId)).thenReturn(Collections.emptyList());

        List<BankAccountDTO> result = bankAccountService.getByAppUserId(userId);

        assertTrue(result.isEmpty());
    }

    // --- getByAppUserIdAndName ---

    @Test
    void getByAppUserIdAndName_withResults_returnsDTOs() {
        when(bankAccountRepository.findByAppUserIdAndName(userId, "Checking")).thenReturn(List.of(bankAccount));

        List<BankAccountDTO> result = bankAccountService.getByAppUserIdAndName(userId, "Checking");

        assertEquals(1, result.size());
        assertEquals("Checking", result.get(0).getName());
    }

    @Test
    void getByAppUserIdAndName_empty_returnsEmptyList() {
        when(bankAccountRepository.findByAppUserIdAndName(userId, "NonExistent")).thenReturn(Collections.emptyList());

        List<BankAccountDTO> result = bankAccountService.getByAppUserIdAndName(userId, "NonExistent");

        assertTrue(result.isEmpty());
    }

    // --- update ---

    @Test
    void update_existing_updatesNameAndBalance() {
        BankAccountDTO updateDTO = new BankAccountDTO(null, userId, "Savings", "CHECKING", new BigDecimal("5000.00"),
                null);
        BankAccount updatedAccount = new BankAccount();
        updatedAccount.setId(accountId);
        updatedAccount.setAppUser(appUser);
        updatedAccount.setName("Savings");
        updatedAccount.setCurrentBalance(new BigDecimal("5000.00"));

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(updatedAccount);

        BankAccountDTO result = bankAccountService.update(accountId, updateDTO);

        assertNotNull(result);
        assertEquals("Savings", result.getName());
        assertEquals(new BigDecimal("5000.00"), result.getCurrentBalance());
    }

    @Test
    void update_balanceChanged_createsAdjustmentTransaction() {
        BankAccountDTO updateDTO = new BankAccountDTO(null, userId, "Checking", "CHECKING", new BigDecimal("800.00"),
                null);
        BankAccount updatedAccount = new BankAccount();
        updatedAccount.setId(accountId);
        updatedAccount.setAppUser(appUser);
        updatedAccount.setName("Checking");
        updatedAccount.setCurrentBalance(new BigDecimal("800.00"));

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(updatedAccount);

        bankAccountService.update(accountId, updateDTO);

        // Balance went from 1000 to 800, so difference is -200
        verify(transactionRepository).save(argThat(txn -> txn.getAmount().compareTo(new BigDecimal("-200.00")) == 0
                && "Adjustment".equals(txn.getDescription())
                && txn.getBankAccount().getId().equals(accountId)
                && txn.getEnvelope() == null));
    }

    @Test
    void update_sameBalance_doesNotCreateTransaction() {
        BankAccountDTO updateDTO = new BankAccountDTO(null, userId, "Savings", "CHECKING", new BigDecimal("1000.00"),
                null);
        BankAccount updatedAccount = new BankAccount();
        updatedAccount.setId(accountId);
        updatedAccount.setAppUser(appUser);
        updatedAccount.setName("Savings");
        updatedAccount.setCurrentBalance(new BigDecimal("1000.00"));

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(updatedAccount);

        bankAccountService.update(accountId, updateDTO);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void update_nonExisting_throwsEntityNotFoundException() {
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bankAccountService.update(accountId, bankAccountDTO));
    }

    @Test
    void update_nullDTO_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.update(accountId, null));
    }

    @Test
    void update_creditCard_ignoresBalanceChange() {
        bankAccount.setAccountType(AccountType.CREDIT_CARD);
        bankAccount.setCurrentBalance(new BigDecimal("500.00"));

        BankAccountDTO updateDTO = new BankAccountDTO(null, userId, "Visa", "CREDIT_CARD", new BigDecimal("999.00"),
                null);

        BankAccount savedAccount = new BankAccount();
        savedAccount.setId(accountId);
        savedAccount.setAppUser(appUser);
        savedAccount.setName("Visa");
        savedAccount.setAccountType(AccountType.CREDIT_CARD);
        savedAccount.setCurrentBalance(new BigDecimal("500.00"));

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(savedAccount);

        BankAccountDTO result = bankAccountService.update(accountId, updateDTO);

        assertEquals("Visa", result.getName());
        // Balance should remain unchanged — CC balances only change via transactions
        assertEquals(new BigDecimal("500.00"), result.getCurrentBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // --- reconcileBalance ---

    @Test
    void reconcileBalance_updatesBalanceAndCreatesAdjustment() {
        bankAccount.setAccountType(AccountType.CREDIT_CARD);
        bankAccount.setCurrentBalance(new BigDecimal("500.00"));

        BankAccount savedAccount = new BankAccount();
        savedAccount.setId(accountId);
        savedAccount.setAppUser(appUser);
        savedAccount.setName("Checking");
        savedAccount.setAccountType(AccountType.CREDIT_CARD);
        savedAccount.setCurrentBalance(new BigDecimal("750.00"));

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(savedAccount);

        BankAccountDTO result = bankAccountService.reconcileBalance(accountId, new BigDecimal("750.00"));

        assertEquals(new BigDecimal("750.00"), result.getCurrentBalance());
        // Difference is 750 - 500 = 250
        verify(transactionRepository).save(argThat(txn -> txn.getAmount().compareTo(new BigDecimal("250.00")) == 0
                && "Balance Adjustment".equals(txn.getDescription())));
    }

    @Test
    void reconcileBalance_sameBalance_noTransaction() {
        bankAccount.setCurrentBalance(new BigDecimal("500.00"));

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));

        BankAccountDTO result = bankAccountService.reconcileBalance(accountId, new BigDecimal("500.00"));

        assertEquals(new BigDecimal("500.00"), result.getCurrentBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(bankAccountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    void reconcileBalance_nullBalance_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> bankAccountService.reconcileBalance(accountId, null));
    }

    @Test
    void reconcileBalance_negativeBalance_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> bankAccountService.reconcileBalance(accountId, new BigDecimal("-100.00")));
    }

    // --- delete ---

    @Test
    void delete_existing_deletesSuccessfully() {
        bankAccount.setAccountType(AccountType.CHECKING);
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));

        bankAccountService.delete(accountId);

        verify(bankAccountRepository).deleteById(accountId);
    }

    @Test
    void delete_nonExisting_throwsEntityNotFoundException() {
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bankAccountService.delete(accountId));
    }

    @Test
    void updateBalance_existingAccount_updatesBalance() {
        UUID accountId = UUID.randomUUID();
        BankAccountRepository repo = mock(BankAccountRepository.class);
        AppUserRepository userRepo = mock(AppUserRepository.class);
        TransactionRepository txnRepo = mock(TransactionRepository.class);
        EnvelopeCategoryRepository catRepo = mock(EnvelopeCategoryRepository.class);
        EnvelopeRepository envRepo = mock(EnvelopeRepository.class);
        BankAccountService service = new BankAccountService(repo, userRepo, txnRepo, catRepo, envRepo);

        BankAccount account = new BankAccount();
        account.setId(accountId);
        account.setCurrentBalance(new BigDecimal("100.00"));

        when(repo.findById(accountId)).thenReturn(Optional.of(account));
        when(repo.save(any(BankAccount.class))).thenReturn(account);

        service.updateBalance(accountId, new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), account.getCurrentBalance());
        verify(repo).save(account);
    }

    @Test
    void updateBalance_nonExistingAccount_throwsException() {
        UUID accountId = UUID.randomUUID();
        BankAccountRepository repo = mock(BankAccountRepository.class);
        AppUserRepository userRepo = mock(AppUserRepository.class);
        TransactionRepository txnRepo = mock(TransactionRepository.class);
        EnvelopeCategoryRepository catRepo = mock(EnvelopeCategoryRepository.class);
        EnvelopeRepository envRepo = mock(EnvelopeRepository.class);
        BankAccountService service = new BankAccountService(repo, userRepo, txnRepo, catRepo, envRepo);

        when(repo.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.updateBalance(accountId, new BigDecimal("10.00")));
    }

    // --- isCreditCard ---

    @Test
    void isCreditCard_creditCardAccount_returnsTrue() {
        BankAccount cc = new BankAccount();
        cc.setId(accountId);
        cc.setAppUser(appUser);
        cc.setName("Visa");
        cc.setAccountType(AccountType.CREDIT_CARD);
        cc.setCurrentBalance(BigDecimal.ZERO);

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(cc));

        assertTrue(bankAccountService.isCreditCard(accountId));
    }

    @Test
    void isCreditCard_checkingAccount_returnsFalse() {
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(bankAccount));

        assertFalse(bankAccountService.isCreditCard(accountId));
    }

    @Test
    void isCreditCard_nonExistingAccount_throwsEntityNotFoundException() {
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> bankAccountService.isCreditCard(accountId));
    }

    // --- create with CREDIT_CARD type ---

    @Test
    void create_creditCard_returnsSavedDTOWithAccountType() {
        BankAccountDTO ccDTO = new BankAccountDTO(null, userId, "Visa", "CREDIT_CARD", BigDecimal.ZERO, null);

        BankAccount ccAccount = new BankAccount();
        ccAccount.setId(accountId);
        ccAccount.setAppUser(appUser);
        ccAccount.setName("Visa");
        ccAccount.setAccountType(AccountType.CREDIT_CARD);
        ccAccount.setCurrentBalance(BigDecimal.ZERO);
        ccAccount.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(ccAccount);

        BankAccountDTO result = bankAccountService.create(ccDTO);

        assertNotNull(result);
        assertEquals("CREDIT_CARD", result.getAccountType());
        assertEquals("Visa", result.getName());
        assertEquals(BigDecimal.ZERO, result.getCurrentBalance());
    }

    @Test
    void create_creditCard_nonZeroBalance_createsNegativeInitialBalanceTransaction() {
        BankAccountDTO ccDTO = new BankAccountDTO(null, userId, "Visa", "CREDIT_CARD", new BigDecimal("20.00"), null);

        BankAccount ccAccount = new BankAccount();
        ccAccount.setId(accountId);
        ccAccount.setAppUser(appUser);
        ccAccount.setName("Visa");
        ccAccount.setAccountType(AccountType.CREDIT_CARD);
        ccAccount.setCurrentBalance(new BigDecimal("20.00"));
        ccAccount.setCreatedAt(ZonedDateTime.now());

        when(appUserRepository.existsById(userId)).thenReturn(true);
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(ccAccount);

        bankAccountService.create(ccDTO);

        verify(transactionRepository).save(argThat(txn -> txn.getAmount().compareTo(new BigDecimal("-20.00")) == 0
                && "Initial Balance".equals(txn.getDescription())
                && txn.getBankAccount().getId().equals(accountId)));
    }

    // --- linkPlaidAccount ---

    @Test
    void linkPlaidAccount_differentBalance_createsAuditTransaction() {
        com.budget.budgetai.model.PlaidItem plaidItem = new com.budget.budgetai.model.PlaidItem();
        plaidItem.setId(UUID.randomUUID());

        BankAccount existingAccount = new BankAccount();
        existingAccount.setId(accountId);
        existingAccount.setAppUser(appUser);
        existingAccount.setName("Checking");
        existingAccount.setAccountType(AccountType.CHECKING);
        existingAccount.setCurrentBalance(new BigDecimal("900.00"));
        existingAccount.setCreatedAt(ZonedDateTime.now());

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(existingAccount);

        bankAccountService.linkPlaidAccount(accountId, plaidItem, "plaid-acc-1", "1234",
                new BigDecimal("1000.00"));

        // Balance difference is 1000 - 900 = 100
        verify(transactionRepository).save(argThat(txn -> txn.getAmount().compareTo(new BigDecimal("100.00")) == 0
                && "Balance Adjustment (Plaid Link)".equals(txn.getDescription())
                && txn.getBankAccount().getId().equals(accountId)));
    }

    @Test
    void linkPlaidAccount_sameBalance_noAuditTransaction() {
        com.budget.budgetai.model.PlaidItem plaidItem = new com.budget.budgetai.model.PlaidItem();
        plaidItem.setId(UUID.randomUUID());

        BankAccount existingAccount = new BankAccount();
        existingAccount.setId(accountId);
        existingAccount.setAppUser(appUser);
        existingAccount.setName("Checking");
        existingAccount.setAccountType(AccountType.CHECKING);
        existingAccount.setCurrentBalance(new BigDecimal("1000.00"));
        existingAccount.setCreatedAt(ZonedDateTime.now());

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(existingAccount);

        bankAccountService.linkPlaidAccount(accountId, plaidItem, "plaid-acc-1", "1234",
                new BigDecimal("1000.00"));

        // No audit transaction when balances match
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
}
