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
import com.plaid.client.model.AccountsBalanceGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.model.CountryCode;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.model.ItemRemoveRequest;
import com.plaid.client.model.LinkTokenCreateRequest;
import com.plaid.client.model.LinkTokenCreateRequestUser;
import com.plaid.client.model.LinkTokenCreateResponse;
import com.plaid.client.model.PersonalFinanceCategory;
import com.plaid.client.model.Products;
import com.plaid.client.model.RemovedTransaction;
import com.plaid.client.model.TransactionsSyncRequest;
import com.plaid.client.model.TransactionsSyncResponse;
import com.plaid.client.request.PlaidApi;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PlaidService {

    private static final Logger log = LoggerFactory.getLogger(PlaidService.class);

    private final PlaidApi plaidApi;
    private final EncryptionService encryptionService;
    private final PlaidItemRepository plaidItemRepository;
    private final AppUserRepository appUserRepository;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final BankAccountService bankAccountService;

    public PlaidService(PlaidApi plaidApi, EncryptionService encryptionService,
            PlaidItemRepository plaidItemRepository, AppUserRepository appUserRepository,
            BankAccountRepository bankAccountRepository, TransactionRepository transactionRepository,
            BankAccountService bankAccountService) {
        this.plaidApi = plaidApi;
        this.encryptionService = encryptionService;
        this.plaidItemRepository = plaidItemRepository;
        this.appUserRepository = appUserRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.transactionRepository = transactionRepository;
        this.bankAccountService = bankAccountService;
    }

    public String createLinkToken(UUID userId) {
        try {
            LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                    .clientUserId(userId.toString());

            LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                    .user(user)
                    .clientName("AI Envelope Budget")
                    .products(List.of(Products.TRANSACTIONS))
                    .countryCodes(List.of(CountryCode.US))
                    .language("en");

            Response<LinkTokenCreateResponse> response = plaidApi.linkTokenCreate(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Failed to create Plaid link token: " +
                        (response.errorBody() != null ? response.errorBody().string() : "Unknown error"));
            }

            return response.body().getLinkToken();
        } catch (IOException e) {
            throw new RuntimeException("Error communicating with Plaid API", e);
        }
    }

    public List<BankAccountDTO> exchangePublicToken(UUID userId, ExchangeTokenRequest request) {
        try {
            // Exchange public token for access token
            ItemPublicTokenExchangeRequest exchangeRequest = new ItemPublicTokenExchangeRequest()
                    .publicToken(request.getPublicToken());

            Response<ItemPublicTokenExchangeResponse> exchangeResponse = plaidApi
                    .itemPublicTokenExchange(exchangeRequest).execute();

            if (!exchangeResponse.isSuccessful() || exchangeResponse.body() == null) {
                throw new RuntimeException("Failed to exchange public token: " +
                        (exchangeResponse.errorBody() != null ? exchangeResponse.errorBody().string()
                                : "Unknown error"));
            }

            String accessToken = exchangeResponse.body().getAccessToken();
            String itemId = exchangeResponse.body().getItemId();

            // Create PlaidItem record with encrypted access token
            AppUser appUser = appUserRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("AppUser not found with id: " + userId));

            PlaidItem plaidItem = new PlaidItem();
            plaidItem.setAppUser(appUser);
            plaidItem.setItemId(itemId);
            plaidItem.setAccessToken(encryptionService.encrypt(accessToken));
            plaidItem.setInstitutionId(request.getInstitutionId());
            plaidItem.setInstitutionName(request.getInstitutionName());
            plaidItem.setStatus(PlaidItemStatus.ACTIVE);
            plaidItem = plaidItemRepository.save(plaidItem);

            // Fetch account balances from Plaid
            AccountsBalanceGetRequest balanceRequest = new AccountsBalanceGetRequest()
                    .accessToken(accessToken);

            Response<AccountsGetResponse> balanceResponse = plaidApi.accountsBalanceGet(balanceRequest).execute();

            if (!balanceResponse.isSuccessful() || balanceResponse.body() == null) {
                throw new RuntimeException("Failed to fetch account balances from Plaid");
            }

            // Map Plaid accounts to bank accounts based on user's linking choices
            Map<String, AccountBase> plaidAccountMap = balanceResponse.body().getAccounts().stream()
                    .collect(Collectors.toMap(AccountBase::getAccountId, a -> a));

            List<BankAccountDTO> linkedAccounts = new ArrayList<>();

            for (PlaidAccountLink link : request.getAccountLinks()) {
                AccountBase plaidAccount = plaidAccountMap.get(link.getPlaidAccountId());
                if (plaidAccount == null) {
                    log.warn("Plaid account not found: {}", link.getPlaidAccountId());
                    continue;
                }

                BigDecimal balance = getBalance(plaidAccount);
                AccountType accountType = mapPlaidAccountType(plaidAccount);
                String mask = plaidAccount.getMask();

                if (link.getExistingBankAccountId() != null) {
                    // Link to existing account
                    BankAccountDTO linked = bankAccountService.linkPlaidAccount(
                            link.getExistingBankAccountId(), plaidItem,
                            link.getPlaidAccountId(), mask, balance);
                    linkedAccounts.add(linked);
                } else {
                    // Create new account
                    BankAccountDTO dto = new BankAccountDTO();
                    dto.setAppUserId(userId);
                    dto.setName(plaidAccount.getName());
                    dto.setAccountType(accountType.name());
                    dto.setCurrentBalance(balance);

                    BankAccountDTO created = bankAccountService.createPlaidAccount(
                            dto, plaidItem, link.getPlaidAccountId(), mask);
                    linkedAccounts.add(created);
                }
            }

            // Trigger initial transaction sync
            syncTransactions(plaidItem, accessToken);

            return linkedAccounts;
        } catch (IOException e) {
            throw new RuntimeException("Error communicating with Plaid API", e);
        }
    }

    public void syncTransactions(PlaidItem plaidItem) {
        String accessToken = encryptionService.decrypt(plaidItem.getAccessToken());
        syncTransactions(plaidItem, accessToken);
    }

    private void syncTransactions(PlaidItem plaidItem, String accessToken) {
        try {
            String cursor = plaidItem.getTransactionCursor();
            boolean hasMore = true;

            while (hasMore) {
                TransactionsSyncRequest syncRequest = new TransactionsSyncRequest()
                        .accessToken(accessToken);
                if (cursor != null) {
                    syncRequest.cursor(cursor);
                }

                Response<TransactionsSyncResponse> syncResponse = plaidApi.transactionsSync(syncRequest).execute();

                if (!syncResponse.isSuccessful() || syncResponse.body() == null) {
                    log.error("Failed to sync transactions for item {}", plaidItem.getItemId());
                    plaidItem.setStatus(PlaidItemStatus.ERROR);
                    plaidItemRepository.save(plaidItem);
                    return;
                }

                TransactionsSyncResponse body = syncResponse.body();

                // Process added transactions
                for (com.plaid.client.model.Transaction plaidTxn : body.getAdded()) {
                    processAddedTransaction(plaidItem, plaidTxn);
                }

                // Process modified transactions
                for (com.plaid.client.model.Transaction plaidTxn : body.getModified()) {
                    processModifiedTransaction(plaidTxn);
                }

                // Process removed transactions
                for (RemovedTransaction removed : body.getRemoved()) {
                    processRemovedTransaction(removed);
                }

                cursor = body.getNextCursor();
                hasMore = body.getHasMore();
            }

            plaidItem.setTransactionCursor(cursor);
            plaidItem.setLastSyncedAt(ZonedDateTime.now());
            plaidItem.setStatus(PlaidItemStatus.ACTIVE);
            plaidItemRepository.save(plaidItem);

        } catch (IOException e) {
            log.error("Error syncing transactions for item {}", plaidItem.getItemId(), e);
            plaidItem.setStatus(PlaidItemStatus.ERROR);
            plaidItemRepository.save(plaidItem);
        }
    }

    public void refreshBalances(PlaidItem plaidItem) {
        try {
            String accessToken = encryptionService.decrypt(plaidItem.getAccessToken());

            AccountsBalanceGetRequest request = new AccountsBalanceGetRequest()
                    .accessToken(accessToken);

            Response<AccountsGetResponse> response = plaidApi.accountsBalanceGet(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                log.error("Failed to refresh balances for item {}", plaidItem.getItemId());
                return;
            }

            for (AccountBase plaidAccount : response.body().getAccounts()) {
                bankAccountRepository.findByPlaidAccountId(plaidAccount.getAccountId())
                        .ifPresent(bankAccount -> {
                            BigDecimal newBalance = getBalance(plaidAccount);
                            bankAccount.setCurrentBalance(newBalance);
                            bankAccountRepository.save(bankAccount);
                        });
            }

        } catch (IOException e) {
            log.error("Error refreshing balances for item {}", plaidItem.getItemId(), e);
        }
    }

    public void unlinkItem(UUID userId, UUID plaidItemId) {
        PlaidItem plaidItem = plaidItemRepository.findByIdAndAppUserId(plaidItemId, userId)
                .orElseThrow(() -> new EntityNotFoundException("PlaidItem not found"));

        try {
            String accessToken = encryptionService.decrypt(plaidItem.getAccessToken());
            ItemRemoveRequest removeRequest = new ItemRemoveRequest().accessToken(accessToken);
            plaidApi.itemRemove(removeRequest).execute();
        } catch (IOException e) {
            log.warn("Failed to remove item from Plaid (may already be removed): {}", e.getMessage());
        }

        // Mark item as revoked and disconnect accounts
        plaidItem.setStatus(PlaidItemStatus.REVOKED);
        plaidItemRepository.save(plaidItem);

        List<BankAccount> linkedAccounts = bankAccountRepository.findByPlaidItemId(plaidItemId);
        for (BankAccount account : linkedAccounts) {
            account.setManual(true);
            account.setPlaidItem(null);
            account.setPlaidAccountId(null);
            bankAccountRepository.save(account);
        }
    }

    public List<PlaidItemDTO> getItemsByUserId(UUID userId) {
        return plaidItemRepository.findByAppUserId(userId).stream()
                .filter(item -> item.getStatus() != PlaidItemStatus.REVOKED)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PlaidItem getPlaidItemEntity(UUID plaidItemId, UUID userId) {
        return plaidItemRepository.findByIdAndAppUserId(plaidItemId, userId)
                .orElseThrow(() -> new EntityNotFoundException("PlaidItem not found"));
    }

    // --- Private helpers ---

    private void processAddedTransaction(PlaidItem plaidItem, com.plaid.client.model.Transaction plaidTxn) {
        // Skip if already exists (deduplication)
        if (transactionRepository.findByPlaidTransactionId(plaidTxn.getTransactionId()).isPresent()) {
            return;
        }

        // Find the linked bank account
        Optional<BankAccount> bankAccountOpt = bankAccountRepository.findByPlaidAccountId(plaidTxn.getAccountId());
        if (bankAccountOpt.isEmpty()) {
            log.warn("No linked bank account found for Plaid account {}", plaidTxn.getAccountId());
            return;
        }
        BankAccount bankAccount = bankAccountOpt.get();

        com.budget.budgetai.model.Transaction transaction = new com.budget.budgetai.model.Transaction();
        transaction.setAppUser(plaidItem.getAppUser());
        transaction.setBankAccount(bankAccount);

        // Plaid amounts: positive = money leaving account (debit), negative = money
        // entering (credit)
        // Our app: negative = expense, positive = income/credit
        // So we negate Plaid's amount to match our convention
        BigDecimal amount = BigDecimal.valueOf(plaidTxn.getAmount()).negate();

        // For credit cards, Plaid already uses the same convention (positive = charges)
        // but our app inverts CC balances, so we need to handle this:
        if (bankAccount.getAccountType() == AccountType.CREDIT_CARD) {
            // For CC: Plaid positive = charge (our negative), Plaid negative =
            // payment/credit (our positive)
            // The negate above already handles this correctly
        }

        transaction.setAmount(amount);
        transaction.setDescription(plaidTxn.getName());
        transaction.setTransactionDate(plaidTxn.getDate());
        transaction.setPlaidTransactionId(plaidTxn.getTransactionId());
        transaction.setPending(plaidTxn.getPending());
        transaction.setMerchantName(plaidTxn.getMerchantName());

        // Build category string from Plaid's personal finance category
        PersonalFinanceCategory category = plaidTxn.getPersonalFinanceCategory();
        if (category != null) {
            transaction.setPlaidCategory(category.getPrimary());
        }

        transactionRepository.save(transaction);

        // Note: We don't adjust bank account balance here because Plaid balances
        // are synced separately via refreshBalances(). Adding balance adjustments
        // would double-count since Plaid's balance already reflects these transactions.
    }

    private void processModifiedTransaction(com.plaid.client.model.Transaction plaidTxn) {
        transactionRepository.findByPlaidTransactionId(plaidTxn.getTransactionId())
                .ifPresent(existing -> {
                    BigDecimal newAmount = BigDecimal.valueOf(plaidTxn.getAmount()).negate();
                    existing.setAmount(newAmount);
                    existing.setDescription(plaidTxn.getName());
                    existing.setTransactionDate(plaidTxn.getDate());
                    existing.setPending(plaidTxn.getPending());
                    existing.setMerchantName(plaidTxn.getMerchantName());

                    PersonalFinanceCategory category = plaidTxn.getPersonalFinanceCategory();
                    if (category != null) {
                        existing.setPlaidCategory(category.getPrimary());
                    }

                    transactionRepository.save(existing);
                });
    }

    private void processRemovedTransaction(RemovedTransaction removed) {
        transactionRepository.findByPlaidTransactionId(removed.getTransactionId())
                .ifPresent(transactionRepository::delete);
    }

    private BigDecimal getBalance(AccountBase account) {
        AccountBalance balances = account.getBalances();
        // For credit accounts, use current balance (amount owed)
        if (account.getType() == com.plaid.client.model.AccountType.CREDIT) {
            return balances.getCurrent() != null
                    ? BigDecimal.valueOf(balances.getCurrent())
                    : BigDecimal.ZERO;
        }
        // For depository accounts, prefer available balance, fall back to current
        if (balances.getAvailable() != null) {
            return BigDecimal.valueOf(balances.getAvailable());
        }
        return balances.getCurrent() != null
                ? BigDecimal.valueOf(balances.getCurrent())
                : BigDecimal.ZERO;
    }

    private AccountType mapPlaidAccountType(AccountBase account) {
        if (account.getType() == null) {
            return AccountType.CHECKING;
        }
        if (account.getType() == com.plaid.client.model.AccountType.CREDIT) {
            return AccountType.CREDIT_CARD;
        }
        if (account.getType() == com.plaid.client.model.AccountType.DEPOSITORY) {
            if (account.getSubtype() != null
                    && account.getSubtype() == AccountSubtype.SAVINGS) {
                return AccountType.SAVINGS;
            }
            return AccountType.CHECKING;
        }
        return AccountType.CHECKING;
    }

    private PlaidItemDTO toDTO(PlaidItem item) {
        List<BankAccountDTO> accounts = bankAccountService.getByPlaidItemId(item.getId());
        return new PlaidItemDTO(
                item.getId(),
                item.getInstitutionId(),
                item.getInstitutionName(),
                item.getStatus().name(),
                item.getLastSyncedAt(),
                item.getCreatedAt(),
                accounts);
    }
}
