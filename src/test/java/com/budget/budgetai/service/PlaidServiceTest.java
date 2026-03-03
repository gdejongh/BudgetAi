package com.budget.budgetai.service;

import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.dto.ExchangeTokenRequest;
import com.budget.budgetai.dto.PlaidAccountLink;
import com.budget.budgetai.dto.PlaidItemDTO;
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
                                                && request.getProducts().contains(Products.BALANCE)
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
                when(bankAccountRepository.findByPlaidAccountId("account-plaid-1"))
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
                when(bankAccountRepository.findByPlaidAccountId("plaid-acc-1"))
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

                // Transaction dated before the account was linked
                LocalDate linkedDate = LocalDate.of(2026, 3, 1);
                com.plaid.client.model.Transaction preLinkTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-old")
                                .accountId("account-plaid-1")
                                .amount(50.0)
                                .name("Old Purchase")
                                .date(linkedDate.minusDays(5))
                                .pending(false);

                // Transaction dated on or after the linked date
                com.plaid.client.model.Transaction postLinkTxn = new com.plaid.client.model.Transaction()
                                .transactionId("txn-new")
                                .accountId("account-plaid-1")
                                .amount(15.0)
                                .name("New Purchase")
                                .date(linkedDate.plusDays(1))
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
                bankAccount.setPlaidLinkedAt(linkedDate.atStartOfDay(java.time.ZoneId.systemDefault()));
                when(bankAccountRepository.findByPlaidAccountId("account-plaid-1"))
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
}
