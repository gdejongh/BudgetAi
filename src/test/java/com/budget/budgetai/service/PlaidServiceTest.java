package com.budget.budgetai.service;

import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.dto.ExchangeTokenRequest;
import com.budget.budgetai.dto.PlaidAccountLink;
import com.budget.budgetai.dto.PlaidItemDTO;
import com.budget.budgetai.dto.SyncResultDTO;
import com.budget.budgetai.model.AccountType;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.BankAccount;
import com.budget.budgetai.model.PlaidItem;
import com.budget.budgetai.model.PlaidItemStatus;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
import com.budget.budgetai.repository.PlaidItemRepository;
import com.budget.budgetai.repository.TransactionRepository;
import com.plaid.client.model.AccountBalance;
import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountSubtype;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.LinkTokenCreateResponse;
import com.plaid.client.model.PersonalFinanceCategory;
import com.plaid.client.model.Products;
import com.plaid.client.model.RemovedTransaction;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.request.PlaidApi;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaidServiceTest {

        @Mock
        private PlaidApi plaidApi;

        @Mock
        private EncryptionService encryptionService;

        @Mock
        private PlaidItemRepository plaidItemRepository;

        @Mock
        private AppUserRepository appUserRepository;

        @Mock
        private BankAccountRepository bankAccountRepository;

        @Mock
        private TransactionRepository transactionRepository;

        @Mock
        private BankAccountService bankAccountService;

        private PlaidService plaidService;

        private UUID userId;
        private AppUser appUser;

        @BeforeEach
        void setUp() {
                plaidService = new PlaidService(plaidApi, encryptionService, plaidItemRepository,
                                appUserRepository, bankAccountRepository, transactionRepository, bankAccountService);

                userId = UUID.randomUUID();
                appUser = new AppUser();
                appUser.setId(userId);
                appUser.setEmail("test@example.com");
        }

        @Test
        @SuppressWarnings("unchecked")
        void createLinkToken_success_returnsLinkToken() throws IOException {
                String expectedToken = "link-sandbox-12345";

                LinkTokenCreateResponse responseBody = new LinkTokenCreateResponse()
                                .linkToken(expectedToken);

                Call<LinkTokenCreateResponse> call = mock(Call.class);
                when(call.execute()).thenReturn(Response.success(responseBody));
                when(plaidApi.linkTokenCreate(any(LinkTokenCreateRequest.class))).thenReturn(call);

                String result = plaidService.createLinkToken(userId);

                assertEquals(expectedToken, result);
                verify(plaidApi)
                                .linkTokenCreate(argThat(request -> request.getUser().getClientUserId()
                                                .equals(userId.toString())
                                                && request.getProducts().contains(Products.TRANSACTIONS)));
        }

        @Test
        @SuppressWarnings("unchecked")
        void createLinkToken_plaidError_throwsException() throws IOException {
                Call<LinkTokenCreateResponse> call = mock(Call.class);
                when(call.execute()).thenReturn(Response.error(400,
                                okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),
                                                "{\"error\":\"bad\"}")));
                when(plaidApi.linkTokenCreate(any())).thenReturn(call);

                assertThrows(RuntimeException.class, () -> plaidService.createLinkToken(userId));
        }

        @Test
        @SuppressWarnings("unchecked")
        void exchangePublicToken_newAccount_createsAccountAndPlaidItem() throws IOException {
                String publicToken = "public-sandbox-token";
                String accessToken = "access-sandbox-token";
                String plaidItemId = "item-sandbox-id";
                String plaidAccountId = "account-plaid-123";
                String encryptedToken = "encrypted-access-token";

                // Mock token exchange
                ItemPublicTokenExchangeResponse exchangeBody = new ItemPublicTokenExchangeResponse()
                                .accessToken(accessToken)
                                .itemId(plaidItemId);
                Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
                when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
                when(plaidApi.itemPublicTokenExchange(any())).thenReturn(exchangeCall);

                // Mock encryption
                when(encryptionService.encrypt(accessToken)).thenReturn(encryptedToken);

                // Mock user lookup
                when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));

                // Mock PlaidItem save
                PlaidItem savedItem = new PlaidItem();
                savedItem.setId(UUID.randomUUID());
                savedItem.setAppUser(appUser);
                savedItem.setItemId(plaidItemId);
                savedItem.setAccessToken(encryptedToken);
                savedItem.setStatus(PlaidItemStatus.ACTIVE);
                when(plaidItemRepository.save(any(PlaidItem.class))).thenReturn(savedItem);

                // Mock balance fetch
                AccountBase plaidAccount = new AccountBase()
                                .accountId(plaidAccountId)
                                .name("My Checking")
                                .mask("4321")
                                .type(com.plaid.client.model.AccountType.DEPOSITORY)
                                .subtype(AccountSubtype.CHECKING)
                                .balances(new AccountBalance().current(1500.00).available(1450.00));

                AccountsGetResponse balanceBody = new AccountsGetResponse()
                                .accounts(List.of(plaidAccount));
                Call<AccountsGetResponse> balanceCall = mock(Call.class);
                when(balanceCall.execute()).thenReturn(Response.success(balanceBody));
                when(plaidApi.accountsBalanceGet(any())).thenReturn(balanceCall);

                // Mock account creation
                BankAccountDTO createdAccount = new BankAccountDTO();
                createdAccount.setId(UUID.randomUUID());
                createdAccount.setName("My Checking");
                createdAccount.setAccountType("CHECKING");
                createdAccount.setCurrentBalance(BigDecimal.valueOf(1450.0));
                createdAccount.setManual(false);
                when(bankAccountService.createPlaidAccount(any(), any(), eq(plaidAccountId), eq("4321")))
                                .thenReturn(createdAccount);

                // Mock transaction sync (empty response)
                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(Collections.emptyList())
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-1");
                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                // Build request
                PlaidAccountLink link = new PlaidAccountLink(plaidAccountId, null, "My Checking", "CHECKING", "4321");
                ExchangeTokenRequest request = new ExchangeTokenRequest(
                                publicToken, "ins_109508", "Test Bank", List.of(link));

                List<BankAccountDTO> result = plaidService.exchangePublicToken(userId, request);

                assertEquals(1, result.size());
                assertEquals("My Checking", result.get(0).getName());
                assertFalse(result.get(0).isManual());

                // Verify PlaidItem was saved with encrypted token
                ArgumentCaptor<PlaidItem> itemCaptor = ArgumentCaptor.forClass(PlaidItem.class);
                verify(plaidItemRepository, atLeastOnce()).save(itemCaptor.capture());
                PlaidItem capturedItem = itemCaptor.getAllValues().get(0);
                assertEquals(encryptedToken, capturedItem.getAccessToken());
                assertEquals(plaidItemId, capturedItem.getItemId());
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_addsNewTransactions() throws IOException {
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                // Build a Plaid transaction
                com.plaid.client.model.Transaction plaidTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-plaid-1")
                                .accountId("account-plaid-1")
                                .amount(25.50)
                                .name("Coffee Shop")
                                .merchantName("Starbucks")
                                .date(LocalDate.now())
                                .pending(false)
                                .personalFinanceCategory(new PersonalFinanceCategory().primary("FOOD_AND_DRINK"));

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(plaidTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-2");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                // Mock bank account lookup
                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CHECKING);
                bankAccount.setPlaidAccountId("account-plaid-1");
                bankAccount.setPlaidLinkedAt(ZonedDateTime.now().minusDays(1));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("account-plaid-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));

                // Mock that this transaction doesn't exist yet
                when(transactionRepository.findByPlaidTransactionId("txn-plaid-1"))
                                .thenReturn(Optional.empty());

                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Verify transaction was saved
                ArgumentCaptor<com.budget.budgetai.model.Transaction> txnCaptor = ArgumentCaptor
                                .forClass(com.budget.budgetai.model.Transaction.class);
                verify(transactionRepository).save(txnCaptor.capture());

                com.budget.budgetai.model.Transaction saved = txnCaptor.getValue();
                // Plaid amount 25.50 (debit) should be negated to -25.50 in our system
                assertEquals(BigDecimal.valueOf(-25.50), saved.getAmount());
                assertEquals("Coffee Shop", saved.getDescription());
                assertEquals("Starbucks", saved.getMerchantName());
                assertEquals("txn-plaid-1", saved.getPlaidTransactionId());
                assertEquals("FOOD_AND_DRINK", saved.getPlaidCategory());
                assertFalse(saved.isPending());

                // Verify balance was adjusted (checking account: amount directly)
                verify(bankAccountService).updateBalance(bankAccount.getId(), BigDecimal.valueOf(-25.50));
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_skipsExistingTransactions() throws IOException {
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                com.plaid.client.model.Transaction plaidTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-existing")
                                .accountId("acc-1")
                                .amount(10.0)
                                .name("Existing")
                                .date(LocalDate.now())
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(plaidTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-3");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                // Mock that this transaction already exists
                when(transactionRepository.findByPlaidTransactionId("txn-existing"))
                                .thenReturn(Optional.of(new com.budget.budgetai.model.Transaction()));

                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Should NOT save a duplicate
                verify(transactionRepository, never()).save(any(com.budget.budgetai.model.Transaction.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void refreshBalances_updatesAccountBalances() throws IOException {
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setItemId("item-1");

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setCurrentBalance(BigDecimal.valueOf(1000.00));
                bankAccount.setPlaidAccountId("plaid-acc-1");

                AccountBase plaidAccount = new AccountBase()
                                .accountId("plaid-acc-1")
                                .type(com.plaid.client.model.AccountType.DEPOSITORY)
                                .balances(new AccountBalance().current(1200.00).available(1150.00));

                AccountsGetResponse response = new AccountsGetResponse()
                                .accounts(List.of(plaidAccount));

                Call<AccountsGetResponse> call = mock(Call.class);
                when(call.execute()).thenReturn(Response.success(response));
                when(plaidApi.accountsBalanceGet(any())).thenReturn(call);
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("plaid-acc-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));
                when(bankAccountRepository.save(any())).thenReturn(bankAccount);

                plaidService.refreshBalances(plaidItem);

                // Should update to available balance (1150) for depository accounts
                verify(bankAccountRepository)
                                .save(argThat(acct -> acct.getCurrentBalance()
                                                .compareTo(BigDecimal.valueOf(1150.0)) == 0));
        }

        @Test
        void unlinkItem_success_marksRevokedAndDisconnectsAccounts() throws IOException {
                UUID plaidItemId = UUID.randomUUID();

                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(plaidItemId);
                plaidItem.setAppUser(appUser);
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(plaidItemRepository.findByIdAndAppUserId(plaidItemId, userId))
                                .thenReturn(Optional.of(plaidItem));
                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                @SuppressWarnings("unchecked")
                Call<com.plaid.client.model.ItemRemoveResponse> removeCall = mock(Call.class);
                when(removeCall.execute())
                                .thenReturn(Response.success(new com.plaid.client.model.ItemRemoveResponse()));
                when(plaidApi.itemRemove(any())).thenReturn(removeCall);

                BankAccount linkedAccount = new BankAccount();
                linkedAccount.setId(UUID.randomUUID());
                linkedAccount.setPlaidItem(plaidItem);
                linkedAccount.setManual(false);
                when(bankAccountRepository.findByPlaidItemId(plaidItemId))
                                .thenReturn(List.of(linkedAccount));
                when(bankAccountRepository.save(any())).thenReturn(linkedAccount);
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.unlinkItem(userId, plaidItemId);

                verify(plaidItemRepository).save(argThat(item -> item.getStatus() == PlaidItemStatus.REVOKED));
                verify(bankAccountRepository).save(argThat(acct -> acct.isManual() && acct.getPlaidItem() == null));
        }

        @Test
        void unlinkItem_notFound_throwsException() {
                UUID plaidItemId = UUID.randomUUID();
                when(plaidItemRepository.findByIdAndAppUserId(plaidItemId, userId))
                                .thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class,
                                () -> plaidService.unlinkItem(userId, plaidItemId));
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_skipsTransactionsBeforePlaidLinkedAt() throws IOException {
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                // Transaction authorized before the account was linked
                LocalDate linkedDate = LocalDate.of(2026, 3, 1);
                com.plaid.client.model.Transaction preLinkTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-old")
                                .accountId("account-plaid-1")
                                .amount(50.0)
                                .name("Old Purchase")
                                .date(linkedDate.minusDays(6))
                                .authorizedDate(linkedDate.minusDays(5))
                                .pending(false);

                // Transaction authorized on or after the linked date
                com.plaid.client.model.Transaction postLinkTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-new")
                                .accountId("account-plaid-1")
                                .amount(15.0)
                                .name("New Purchase")
                                .date(linkedDate)
                                .authorizedDate(linkedDate.plusDays(1))
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(preLinkTxn, postLinkTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-4");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CHECKING);
                bankAccount.setPlaidAccountId("account-plaid-1");
                bankAccount.setPlaidLinkedAt(linkedDate.atStartOfDay(java.time.ZoneOffset.UTC));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("account-plaid-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));

                when(transactionRepository.findByPlaidTransactionId(anyString()))
                                .thenReturn(Optional.empty());
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Only the post-link transaction should be saved
                ArgumentCaptor<com.budget.budgetai.model.Transaction> txnCaptor = ArgumentCaptor
                                .forClass(com.budget.budgetai.model.Transaction.class);
                verify(transactionRepository, times(1)).save(txnCaptor.capture());

                com.budget.budgetai.model.Transaction saved = txnCaptor.getValue();
                assertEquals("txn-new", saved.getPlaidTransactionId());
                assertEquals("New Purchase", saved.getDescription());

                // Verify balance was adjusted for the post-link transaction only
                verify(bankAccountService, times(1)).updateBalance(eq(bankAccount.getId()), any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_includesSameDayTransactionsWhenLinkedAtIsUTC() throws IOException {
                // Scenario: user links at 8pm ET on March 2 → stored as March 3 01:00 UTC.
                // A transaction dated March 2 (Plaid's bank-local date) should NOT be skipped
                // because the ET-converted linked date is still March 2.
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                // Transaction on the same calendar day as the link (in ET)
                LocalDate txnDate = LocalDate.of(2026, 3, 2);
                com.plaid.client.model.Transaction sameDayTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-same-day")
                                .accountId("account-plaid-1")
                                .amount(30.0)
                                .name("Same Day Purchase")
                                .date(txnDate)
                                .authorizedDate(txnDate)
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(sameDayTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-tz");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CHECKING);
                bankAccount.setPlaidAccountId("account-plaid-1");
                // 8pm ET on March 2 = March 3 01:00 UTC
                bankAccount.setPlaidLinkedAt(
                                java.time.ZonedDateTime.of(2026, 3, 3, 1, 0, 0, 0, java.time.ZoneOffset.UTC));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("account-plaid-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));

                when(transactionRepository.findByPlaidTransactionId(anyString()))
                                .thenReturn(Optional.empty());
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // The same-day transaction should be saved (not skipped)
                ArgumentCaptor<com.budget.budgetai.model.Transaction> txnCaptor = ArgumentCaptor
                                .forClass(com.budget.budgetai.model.Transaction.class);
                verify(transactionRepository, times(1)).save(txnCaptor.capture());

                com.budget.budgetai.model.Transaction saved = txnCaptor.getValue();
                assertEquals("txn-same-day", saved.getPlaidTransactionId());
                assertEquals("Same Day Purchase", saved.getDescription());

                // Verify balance was adjusted
                verify(bankAccountService).updateBalance(eq(bankAccount.getId()), any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_modifiedTransaction_adjustsBalanceDelta() throws IOException {
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                // Modified transaction: amount changed from 25.50 to 30.00
                com.plaid.client.model.Transaction modifiedPlaidTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-modified")
                                .accountId("acc-1")
                                .amount(30.0)
                                .name("Updated Coffee Shop")
                                .date(LocalDate.now())
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(Collections.emptyList())
                                .modified(List.of(modifiedPlaidTxn))
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-mod");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                // Existing transaction in DB with old amount
                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CHECKING);

                com.budget.budgetai.model.Transaction existingTxn = new com.budget.budgetai.model.Transaction();
                existingTxn.setId(UUID.randomUUID());
                existingTxn.setAmount(BigDecimal.valueOf(-25.50)); // old amount (negated Plaid 25.50)
                existingTxn.setBankAccount(bankAccount);
                existingTxn.setPlaidTransactionId("txn-modified");

                when(transactionRepository.findByPlaidTransactionId("txn-modified"))
                                .thenReturn(Optional.of(existingTxn));
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Verify transaction was updated
                verify(transactionRepository).save(existingTxn);
                assertEquals(BigDecimal.valueOf(-30.0), existingTxn.getAmount());
                assertEquals("Updated Coffee Shop", existingTxn.getDescription());

                // Verify balance delta: new (-30.0) - old (-25.50) = -4.50
                verify(bankAccountService).updateBalance(bankAccount.getId(), BigDecimal.valueOf(-4.50));
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_removedTransaction_reversesBalance() throws IOException {
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                RemovedTransaction removedPlaidTxn = new RemovedTransaction()
                                .transactionId("txn-removed");

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(Collections.emptyList())
                                .modified(Collections.emptyList())
                                .removed(List.of(removedPlaidTxn))
                                .hasMore(false)
                                .nextCursor("cursor-rem");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                // Existing transaction to be removed
                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CHECKING);

                com.budget.budgetai.model.Transaction existingTxn = new com.budget.budgetai.model.Transaction();
                existingTxn.setId(UUID.randomUUID());
                existingTxn.setAmount(BigDecimal.valueOf(-25.50)); // expense
                existingTxn.setBankAccount(bankAccount);
                existingTxn.setPlaidTransactionId("txn-removed");

                when(transactionRepository.findByPlaidTransactionId("txn-removed"))
                                .thenReturn(Optional.of(existingTxn));
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Verify transaction was deleted
                verify(transactionRepository).delete(existingTxn);

                // Verify balance was reversed: checking account, amount was -25.50,
                // reversal = -(-25.50) = +25.50
                verify(bankAccountService).updateBalance(bankAccount.getId(), BigDecimal.valueOf(25.50));
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_creditCard_adjustsBalanceCorrectly() throws IOException {
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                // CC charge: Plaid amount 50.0 (charge) → our amount -50.0
                com.plaid.client.model.Transaction plaidTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-cc-1")
                                .accountId("cc-account-1")
                                .amount(50.0)
                                .name("CC Purchase")
                                .date(LocalDate.now())
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(plaidTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-cc");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount ccAccount = new BankAccount();
                ccAccount.setId(UUID.randomUUID());
                ccAccount.setAppUser(appUser);
                ccAccount.setAccountType(AccountType.CREDIT_CARD);
                ccAccount.setPlaidAccountId("cc-account-1");
                ccAccount.setPlaidLinkedAt(ZonedDateTime.now().minusDays(1));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("cc-account-1", plaidItem.getId()))
                                .thenReturn(Optional.of(ccAccount));

                when(transactionRepository.findByPlaidTransactionId("txn-cc-1"))
                                .thenReturn(Optional.empty());
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Verify transaction saved with negated amount
                ArgumentCaptor<com.budget.budgetai.model.Transaction> txnCaptor = ArgumentCaptor
                                .forClass(com.budget.budgetai.model.Transaction.class);
                verify(transactionRepository).save(txnCaptor.capture());
                assertEquals(BigDecimal.valueOf(-50.0), txnCaptor.getValue().getAmount());

                // For CC: balance update = amount.negate() = -(-50.0) = +50.0
                // (CC balance increases when charges are made)
                verify(bankAccountService).updateBalance(ccAccount.getId(), BigDecimal.valueOf(50.0));
        }

        @Test
        @SuppressWarnings("unchecked")
        void exchangePublicToken_runsInitialSyncWithoutBalanceAdjustment() throws IOException {
                String publicToken = "public-sandbox-token";
                String accessToken = "access-sandbox-token";
                String plaidItemId = "item-sandbox-id";
                String plaidAccountId = "account-plaid-123";
                String encryptedToken = "encrypted-access-token";

                // Mock token exchange
                ItemPublicTokenExchangeResponse exchangeBody = new ItemPublicTokenExchangeResponse()
                                .accessToken(accessToken)
                                .itemId(plaidItemId);
                Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
                when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
                when(plaidApi.itemPublicTokenExchange(any())).thenReturn(exchangeCall);

                when(encryptionService.encrypt(accessToken)).thenReturn(encryptedToken);
                when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));

                PlaidItem savedItem = new PlaidItem();
                savedItem.setId(UUID.randomUUID());
                savedItem.setAppUser(appUser);
                savedItem.setItemId(plaidItemId);
                savedItem.setAccessToken(encryptedToken);
                savedItem.setStatus(PlaidItemStatus.ACTIVE);
                when(plaidItemRepository.save(any(PlaidItem.class))).thenReturn(savedItem);

                // Mock balance fetch
                AccountBase plaidAccount = new AccountBase()
                                .accountId(plaidAccountId)
                                .name("My Checking")
                                .mask("4321")
                                .type(com.plaid.client.model.AccountType.DEPOSITORY)
                                .subtype(AccountSubtype.CHECKING)
                                .balances(new AccountBalance().current(1500.00).available(1450.00));

                AccountsGetResponse balanceBody = new AccountsGetResponse()
                                .accounts(List.of(plaidAccount));
                Call<AccountsGetResponse> balanceCall = mock(Call.class);
                when(balanceCall.execute()).thenReturn(Response.success(balanceBody));
                when(plaidApi.accountsBalanceGet(any())).thenReturn(balanceCall);

                BankAccountDTO createdAccount = new BankAccountDTO();
                createdAccount.setId(UUID.randomUUID());
                createdAccount.setName("My Checking");
                createdAccount.setAccountType("CHECKING");
                createdAccount.setCurrentBalance(BigDecimal.valueOf(1450.0));
                createdAccount.setManual(false);
                when(bankAccountService.createPlaidAccount(any(), any(), eq(plaidAccountId), eq("4321")))
                                .thenReturn(createdAccount);

                // Mock initial sync: returns a same-day transaction
                com.plaid.client.model.Transaction sameDayTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-initial-1")
                                .accountId(plaidAccountId)
                                .amount(25.0)
                                .name("Same Day Coffee")
                                .date(LocalDate.now())
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(sameDayTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("initial-cursor-1");
                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CHECKING);
                bankAccount.setPlaidAccountId(plaidAccountId);
                bankAccount.setPlaidLinkedAt(ZonedDateTime.now());
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId(eq(plaidAccountId), any(UUID.class)))
                                .thenReturn(Optional.of(bankAccount));
                when(transactionRepository.findByPlaidTransactionId("txn-initial-1"))
                                .thenReturn(Optional.empty());

                PlaidAccountLink link = new PlaidAccountLink(plaidAccountId, null, "My Checking", "CHECKING", "4321");
                ExchangeTokenRequest request = new ExchangeTokenRequest(
                                publicToken, "ins_109508", "Test Bank", List.of(link));

                List<BankAccountDTO> result = plaidService.exchangePublicToken(userId, request);

                assertEquals(1, result.size());

                // Verify transaction was saved (from initial sync)
                verify(transactionRepository).save(any(com.budget.budgetai.model.Transaction.class));

                // Verify balance was NOT adjusted (initial snapshot transactions are already
                // reflected in the Plaid balance)
                verify(bankAccountService, never()).updateBalance(any(), any());

                // Verify cursor was saved
                ArgumentCaptor<PlaidItem> itemCaptor = ArgumentCaptor.forClass(PlaidItem.class);
                verify(plaidItemRepository, atLeastOnce()).save(itemCaptor.capture());
                List<PlaidItem> savedItems = itemCaptor.getAllValues();
                // The last save should have the cursor from the initial sync
                PlaidItem lastSaved = savedItems.get(savedItems.size() - 1);
                assertEquals("initial-cursor-1", lastSaved.getTransactionCursor());
        }

        @Test
        void getItemsByUserId_filtersRevokedItems() {
                PlaidItem activeItem = new PlaidItem();
                activeItem.setId(UUID.randomUUID());
                activeItem.setStatus(PlaidItemStatus.ACTIVE);
                activeItem.setInstitutionName("Test Bank");

                PlaidItem revokedItem = new PlaidItem();
                revokedItem.setId(UUID.randomUUID());
                revokedItem.setStatus(PlaidItemStatus.REVOKED);

                when(plaidItemRepository.findByAppUserId(userId))
                                .thenReturn(List.of(activeItem, revokedItem));
                when(bankAccountService.getByPlaidItemId(activeItem.getId()))
                                .thenReturn(Collections.emptyList());

                List<PlaidItemDTO> result = plaidService.getItemsByUserId(userId);

                assertEquals(1, result.size());
                assertEquals("Test Bank", result.get(0).getInstitutionName());
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncAllItems_syncsActiveItemsAndRefreshesBalances() throws IOException {
                PlaidItem activeItem = new PlaidItem();
                activeItem.setId(UUID.randomUUID());
                activeItem.setAppUser(appUser);
                activeItem.setItemId("item-1");
                activeItem.setAccessToken("encrypted-token");
                activeItem.setStatus(PlaidItemStatus.ACTIVE);
                activeItem.setTransactionCursor("existing-cursor");

                PlaidItem revokedItem = new PlaidItem();
                revokedItem.setId(UUID.randomUUID());
                revokedItem.setStatus(PlaidItemStatus.REVOKED);

                when(plaidItemRepository.findByAppUserId(userId))
                                .thenReturn(List.of(activeItem, revokedItem));
                when(encryptionService.decrypt("encrypted-token")).thenReturn("decrypted-token");

                // Mock transactions sync (empty response)
                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(Collections.emptyList())
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("new-cursor");
                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                SyncResultDTO result = plaidService.syncAllItems(userId);

                assertEquals(1, result.itemsSynced());
                assertEquals(0, result.itemsFailed());
                assertNotNull(result.message());
                // Verify sync was called (transactionsSync)
                verify(plaidApi).transactionsSync(any());
        }

        @Test
        void syncAllItems_noActiveItems_returnsZero() {
                when(plaidItemRepository.findByAppUserId(userId))
                                .thenReturn(Collections.emptyList());

                SyncResultDTO result = plaidService.syncAllItems(userId);

                assertEquals(0, result.itemsSynced());
                assertEquals(0, result.itemsFailed());
                assertEquals("No active Plaid connections to sync", result.message());
                verifyNoInteractions(plaidApi);
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncAllItems_partialFailure_reportsCorrectCounts() throws IOException {
                PlaidItem goodItem = new PlaidItem();
                goodItem.setId(UUID.randomUUID());
                goodItem.setAppUser(appUser);
                goodItem.setItemId("item-good");
                goodItem.setAccessToken("encrypted-good");
                goodItem.setStatus(PlaidItemStatus.ACTIVE);
                goodItem.setTransactionCursor("cursor-good");

                PlaidItem badItem = new PlaidItem();
                badItem.setId(UUID.randomUUID());
                badItem.setAppUser(appUser);
                badItem.setItemId("item-bad");
                badItem.setAccessToken("encrypted-bad");
                badItem.setStatus(PlaidItemStatus.ACTIVE);
                badItem.setTransactionCursor("cursor-bad");

                when(plaidItemRepository.findByAppUserId(userId))
                                .thenReturn(List.of(goodItem, badItem));

                // Good item succeeds
                when(encryptionService.decrypt("encrypted-good")).thenReturn("decrypted-good");
                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(Collections.emptyList())
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("new-cursor");
                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));

                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                // Bad item fails on decrypt
                when(encryptionService.decrypt("encrypted-bad"))
                                .thenThrow(new RuntimeException("Decryption failed"));

                SyncResultDTO result = plaidService.syncAllItems(userId);

                assertEquals(1, result.itemsSynced());
                assertEquals(1, result.itemsFailed());
                assertTrue(result.message().contains("1 succeeded"));
                assertTrue(result.message().contains("1 failed"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_usesLaterOfAuthorizedAndPostedDate_authorizedAfter() throws IOException {
                // Scenario: posted date is before link, but authorized date is on/after link.
                // The transaction should be saved because we use the LATER of the two dates.
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                LocalDate linkedDate = LocalDate.of(2026, 3, 3);
                // Posted date is March 2 (before link), but authorized date is March 3 (link
                // day)
                com.plaid.client.model.Transaction txn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-auth-date")
                                .accountId("account-plaid-1")
                                .amount(5.0)
                                .name("Anthropic Charge")
                                .date(linkedDate.minusDays(1))
                                .authorizedDate(linkedDate)
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(txn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-auth");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CREDIT_CARD);
                bankAccount.setPlaidAccountId("account-plaid-1");
                bankAccount.setPlaidLinkedAt(linkedDate.atStartOfDay(java.time.ZoneOffset.UTC));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("account-plaid-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));

                when(transactionRepository.findByPlaidTransactionId(anyString()))
                                .thenReturn(Optional.empty());
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Transaction should be saved because max(authorizedDate=March 3, date=March 2)
                // = March 3 >= linkedDate (March 3)
                ArgumentCaptor<com.budget.budgetai.model.Transaction> txnCaptor = ArgumentCaptor
                                .forClass(com.budget.budgetai.model.Transaction.class);
                verify(transactionRepository, times(1)).save(txnCaptor.capture());

                com.budget.budgetai.model.Transaction saved = txnCaptor.getValue();
                assertEquals("txn-auth-date", saved.getPlaidTransactionId());
                assertEquals("Anthropic Charge", saved.getDescription());

                verify(bankAccountService, times(1)).updateBalance(eq(bankAccount.getId()), any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_usesLaterOfAuthorizedAndPostedDate_postedAfter() throws IOException {
                // Scenario: authorized date is BEFORE link, but posted date is on/after link.
                // The transaction should be saved because we use the LATER of the two dates.
                // This matches the real Anthropic charge scenario: authorized March 2, posted
                // March 3, linked March 3.
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                LocalDate linkedDate = LocalDate.of(2026, 3, 3);
                // Authorized March 2 (before link), posted March 3 (link day)
                com.plaid.client.model.Transaction txn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-posted-after")
                                .accountId("account-plaid-1")
                                .amount(5.0)
                                .name("Anthropic API")
                                .date(linkedDate) // posted March 3
                                .authorizedDate(linkedDate.minusDays(1)) // authorized March 2
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(txn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-posted");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CREDIT_CARD);
                bankAccount.setPlaidAccountId("account-plaid-1");
                bankAccount.setPlaidLinkedAt(linkedDate.atStartOfDay(java.time.ZoneOffset.UTC));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("account-plaid-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));

                when(transactionRepository.findByPlaidTransactionId(anyString()))
                                .thenReturn(Optional.empty());
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Transaction should be saved because max(authorizedDate=March 2, date=March 3)
                // = March 3 >= linkedDate (March 3)
                ArgumentCaptor<com.budget.budgetai.model.Transaction> txnCaptor = ArgumentCaptor
                                .forClass(com.budget.budgetai.model.Transaction.class);
                verify(transactionRepository, times(1)).save(txnCaptor.capture());

                com.budget.budgetai.model.Transaction saved = txnCaptor.getValue();
                assertEquals("txn-posted-after", saved.getPlaidTransactionId());
                assertEquals("Anthropic API", saved.getDescription());

                verify(bankAccountService, times(1)).updateBalance(eq(bankAccount.getId()), any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void syncTransactions_fallsBackToDateWhenAuthorizedDateIsNull() throws IOException {
                // Scenario: authorizedDate is null, so the filter should fall back to date.
                // If the posted date is before the link date, the transaction should be
                // skipped.
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(UUID.randomUUID());
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-id");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);

                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                LocalDate linkedDate = LocalDate.of(2026, 3, 3);
                // No authorizedDate, posted date is before link
                com.plaid.client.model.Transaction preLinkTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-no-auth-date")
                                .accountId("account-plaid-1")
                                .amount(25.0)
                                .name("Old Charge No Auth Date")
                                .date(linkedDate.minusDays(5))
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(preLinkTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("cursor-fallback");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CHECKING);
                bankAccount.setPlaidAccountId("account-plaid-1");
                bankAccount.setPlaidLinkedAt(linkedDate.atStartOfDay(java.time.ZoneOffset.UTC));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("account-plaid-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));

                when(transactionRepository.findByPlaidTransactionId(anyString()))
                                .thenReturn(Optional.empty());
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.syncTransactions(plaidItem);

                // Transaction should NOT be saved because date (Feb 26) < linkedDate (March 3)
                // and authorizedDate is null so we fall back to date
                verify(transactionRepository, never()).save(any(com.budget.budgetai.model.Transaction.class));
                verify(bankAccountService, never()).updateBalance(any(), any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void resyncFromScratch_resetsCursorAndSyncsWithoutBalanceAdjustment() throws IOException {
                // Scenario: a transaction was missed due to the pre-link date filter.
                // resyncFromScratch resets the cursor and re-syncs with adjustBalance=false.
                UUID plaidItemId = UUID.randomUUID();
                PlaidItem plaidItem = new PlaidItem();
                plaidItem.setId(plaidItemId);
                plaidItem.setAppUser(appUser);
                plaidItem.setItemId("item-resync");
                plaidItem.setAccessToken("encrypted-token");
                plaidItem.setStatus(PlaidItemStatus.ACTIVE);
                plaidItem.setTransactionCursor("old-cursor");

                when(plaidItemRepository.findByIdAndAppUserId(plaidItemId, userId))
                                .thenReturn(Optional.of(plaidItem));
                when(encryptionService.decrypt("encrypted-token")).thenReturn("access-token");

                LocalDate linkedDate = LocalDate.of(2026, 3, 3);

                // An already-saved transaction (will be deduped)
                com.plaid.client.model.Transaction existingTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-existing")
                                .accountId("account-plaid-1")
                                .amount(33.9)
                                .name("Existing Purchase")
                                .date(linkedDate)
                                .authorizedDate(linkedDate)
                                .pending(false);

                // A missed transaction (authorizedDate on link day, should now be saved)
                com.plaid.client.model.Transaction missedTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-missed")
                                .accountId("account-plaid-1")
                                .amount(5.0)
                                .name("Anthropic Charge")
                                .date(linkedDate.minusDays(1))
                                .authorizedDate(linkedDate)
                                .pending(false);

                TransactionsSyncResponse syncBody = new TransactionsSyncResponse()
                                .added(List.of(existingTxn, missedTxn))
                                .modified(Collections.emptyList())
                                .removed(Collections.emptyList())
                                .hasMore(false)
                                .nextCursor("new-cursor");

                Call<TransactionsSyncResponse> syncCall = mock(Call.class);
                when(syncCall.execute()).thenReturn(Response.success(syncBody));
                when(plaidApi.transactionsSync(any())).thenReturn(syncCall);

                BankAccount bankAccount = new BankAccount();
                bankAccount.setId(UUID.randomUUID());
                bankAccount.setAppUser(appUser);
                bankAccount.setAccountType(AccountType.CREDIT_CARD);
                bankAccount.setPlaidAccountId("account-plaid-1");
                bankAccount.setPlaidLinkedAt(linkedDate.atStartOfDay(java.time.ZoneOffset.UTC));
                when(bankAccountRepository.findByPlaidAccountIdAndPlaidItemId("account-plaid-1", plaidItem.getId()))
                                .thenReturn(Optional.of(bankAccount));

                // Existing txn is already in DB (dedup), missed txn is not
                when(transactionRepository.findByPlaidTransactionId("txn-existing"))
                                .thenReturn(Optional.of(new com.budget.budgetai.model.Transaction()));
                when(transactionRepository.findByPlaidTransactionId("txn-missed"))
                                .thenReturn(Optional.empty());
                when(plaidItemRepository.save(any())).thenReturn(plaidItem);

                plaidService.resyncFromScratch(userId, plaidItemId);

                // Cursor should have been reset to null first
                // Only the missed transaction should be saved (existing is deduped)
                ArgumentCaptor<com.budget.budgetai.model.Transaction> txnCaptor = ArgumentCaptor
                                .forClass(com.budget.budgetai.model.Transaction.class);
                verify(transactionRepository, times(1)).save(txnCaptor.capture());

                com.budget.budgetai.model.Transaction saved = txnCaptor.getValue();
                assertEquals("txn-missed", saved.getPlaidTransactionId());
                assertEquals("Anthropic Charge", saved.getDescription());

                // Balance should NOT be adjusted (adjustBalance=false for resync)
                verify(bankAccountService, never()).updateBalance(any(), any());

                // Cursor should be updated to the new value
                assertEquals("new-cursor", plaidItem.getTransactionCursor());
        }
}
