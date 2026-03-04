package com.budget.budgetai.service;

import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.model.AccountType;
import com.budget.budgetai.model.BankAccount;
import com.budget.budgetai.model.Envelope;
import com.budget.budgetai.model.EnvelopeCategory;
import com.budget.budgetai.model.EnvelopeType;
import com.budget.budgetai.model.Transaction;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
import com.budget.budgetai.repository.EnvelopeCategoryRepository;
import com.budget.budgetai.repository.EnvelopeRepository;
import com.budget.budgetai.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BankAccountService {

    private static final String CC_PAYMENT_CATEGORY_NAME = "Credit Card Payments";

    private final BankAccountRepository bankAccountRepository;
    private final AppUserRepository appUserRepository;
    private final TransactionRepository transactionRepository;
    private final EnvelopeCategoryRepository envelopeCategoryRepository;
    private final EnvelopeRepository envelopeRepository;

    public BankAccountService(BankAccountRepository bankAccountRepository, AppUserRepository appUserRepository,
            TransactionRepository transactionRepository,
            EnvelopeCategoryRepository envelopeCategoryRepository,
            EnvelopeRepository envelopeRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.appUserRepository = appUserRepository;
        this.transactionRepository = transactionRepository;
        this.envelopeCategoryRepository = envelopeCategoryRepository;
        this.envelopeRepository = envelopeRepository;
    }

    public BankAccountDTO create(BankAccountDTO bankAccountDTO) {
        if (bankAccountDTO == null) {
            throw new IllegalArgumentException("BankAccountDTO cannot be null");
        }
        if (bankAccountDTO.getName() == null || bankAccountDTO.getName().isBlank()) {
            throw new IllegalArgumentException("Bank account name cannot be null or empty");
        }
        if (bankAccountDTO.getCurrentBalance() == null) {
            throw new IllegalArgumentException("Current balance cannot be null");
        }
        if (bankAccountDTO.getAppUserId() == null) {
            throw new IllegalArgumentException("App user ID cannot be null");
        }
        BankAccount bankAccount = toEntity(bankAccountDTO);
        BankAccount savedAccount = bankAccountRepository.save(bankAccount);

        if (savedAccount.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0) {
            // For credit cards, the balance represents debt owed, so negate it
            // so the initial balance transaction appears as a negative (expense).
            BigDecimal initialAmount = savedAccount.getAccountType() == AccountType.CREDIT_CARD
                    ? savedAccount.getCurrentBalance().negate()
                    : savedAccount.getCurrentBalance();
            createAuditTransaction(savedAccount, initialAmount, "Initial Balance");
        }

        // Auto-create CC Payment envelope when creating a credit card account
        if (savedAccount.getAccountType() == AccountType.CREDIT_CARD) {
            createCCPaymentEnvelope(savedAccount);
        }

        return toDTO(savedAccount);
    }

    public BankAccountDTO getById(UUID id) {
        return bankAccountRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + id));
    }

    public List<BankAccountDTO> getAll() {
        return bankAccountRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BankAccountDTO> getByAppUserId(UUID appUserId) {
        return bankAccountRepository.findByAppUserId(appUserId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BankAccountDTO> getByAppUserIdAndName(UUID appUserId, String name) {
        return bankAccountRepository.findByAppUserIdAndName(appUserId, name).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public BankAccountDTO update(UUID id, BankAccountDTO bankAccountDTO) {
        if (bankAccountDTO == null) {
            throw new IllegalArgumentException("BankAccountDTO cannot be null");
        }
        BankAccount bankAccount = bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + id));

        BigDecimal oldBalance = bankAccount.getCurrentBalance();

        String oldName = bankAccount.getName();
        bankAccount.setName(bankAccountDTO.getName());

        // Credit card balances should only change through transactions, not manual
        // edits
        if (bankAccount.getAccountType() != AccountType.CREDIT_CARD) {
            bankAccount.setCurrentBalance(bankAccountDTO.getCurrentBalance());
        }
        BankAccount updatedAccount = bankAccountRepository.save(bankAccount);

        // Keep the linked CC_PAYMENT envelope name in sync when the card name changes
        if (updatedAccount.getAccountType() == AccountType.CREDIT_CARD
                && !updatedAccount.getName().equals(oldName)) {
            envelopeRepository.findByLinkedAccountId(updatedAccount.getId()).ifPresent(envelope -> {
                envelope.setName(updatedAccount.getName() + " Payment");
                envelopeRepository.save(envelope);
            });
        }

        BigDecimal difference = updatedAccount.getCurrentBalance().subtract(oldBalance);
        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            createAuditTransaction(updatedAccount, difference, "Adjustment");
        }

        return toDTO(updatedAccount);
    }

    public BankAccountDTO reconcileBalance(UUID id, BigDecimal targetBalance) {
        if (targetBalance == null) {
            throw new IllegalArgumentException("Target balance cannot be null");
        }
        if (targetBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Target balance cannot be negative");
        }
        BankAccount bankAccount = bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + id));

        BigDecimal oldBalance = bankAccount.getCurrentBalance();
        BigDecimal difference = targetBalance.subtract(oldBalance);

        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            return toDTO(bankAccount);
        }

        bankAccount.setCurrentBalance(targetBalance);
        BankAccount updatedAccount = bankAccountRepository.save(bankAccount);
        createAuditTransaction(updatedAccount, difference, "Balance Adjustment");

        return toDTO(updatedAccount);
    }

    public void updateBalance(UUID id, BigDecimal balance) {
        BankAccount b = bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + id));
        BigDecimal updatedBalance = b.getCurrentBalance().add(balance);
        b.setCurrentBalance(updatedBalance);
        bankAccountRepository.save(b);
    }

    public void delete(UUID id) {
        BankAccount account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + id));

        // If deleting a credit card, clean up its linked CC Payment envelope
        if (account.getAccountType() == AccountType.CREDIT_CARD) {
            envelopeRepository.findByLinkedAccountId(id).ifPresent(envelope -> {
                envelopeRepository.delete(envelope);
            });

            // If no more CC Payment envelopes exist for this user, clean up the category
            UUID appUserId = account.getAppUser().getId();
            List<Envelope> remainingCCEnvelopes = envelopeRepository
                    .findByAppUserIdAndEnvelopeType(appUserId, EnvelopeType.CC_PAYMENT);
            // Filter out the one we just deleted (may still be in persistence context)
            remainingCCEnvelopes.removeIf(e -> e.getLinkedAccount() != null && e.getLinkedAccount().getId().equals(id));
            if (remainingCCEnvelopes.isEmpty()) {
                List<EnvelopeCategory> ccCategories = envelopeCategoryRepository
                        .findByAppUserIdAndCategoryType(appUserId, EnvelopeType.CC_PAYMENT);
                ccCategories.forEach(envelopeCategoryRepository::delete);
            }
        }

        bankAccountRepository.deleteById(id);
    }

    private void createCCPaymentEnvelope(BankAccount creditCard) {
        UUID appUserId = creditCard.getAppUser().getId();

        // Find or create the "Credit Card Payments" category
        List<EnvelopeCategory> existingCategories = envelopeCategoryRepository
                .findByAppUserIdAndCategoryType(appUserId, EnvelopeType.CC_PAYMENT);
        EnvelopeCategory ccCategory;
        if (existingCategories.isEmpty()) {
            ccCategory = new EnvelopeCategory();
            ccCategory.setAppUser(creditCard.getAppUser());
            ccCategory.setName(CC_PAYMENT_CATEGORY_NAME);
            ccCategory.setCategoryType(EnvelopeType.CC_PAYMENT);
            ccCategory = envelopeCategoryRepository.save(ccCategory);
        } else {
            ccCategory = existingCategories.get(0);
        }

        // Create the CC Payment envelope linked to this credit card
        Envelope envelope = new Envelope();
        envelope.setAppUser(creditCard.getAppUser());
        envelope.setEnvelopeCategory(ccCategory);
        envelope.setName(creditCard.getName() + " Payment");
        envelope.setAllocatedBalance(BigDecimal.ZERO);
        envelope.setEnvelopeType(EnvelopeType.CC_PAYMENT);
        envelope.setLinkedAccount(creditCard);
        envelopeRepository.save(envelope);
    }

    private void createAuditTransaction(BankAccount bankAccount, BigDecimal amount, String description) {
        Transaction transaction = new Transaction();
        transaction.setAppUser(bankAccount.getAppUser());
        transaction.setBankAccount(bankAccount);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setTransactionDate(LocalDate.now());
        transactionRepository.save(transaction);
    }

    // Mapper methods
    private BankAccountDTO toDTO(BankAccount bankAccount) {
        if (bankAccount == null) {
            return null;
        }
        BankAccountDTO dto = new BankAccountDTO(
                bankAccount.getId(),
                bankAccount.getAppUser().getId(),
                bankAccount.getName(),
                bankAccount.getAccountType() != null ? bankAccount.getAccountType().name()
                        : AccountType.CHECKING.name(),
                bankAccount.getCurrentBalance(),
                bankAccount.getCreatedAt());
        dto.setPlaidAccountId(bankAccount.getPlaidAccountId());
        dto.setPlaidItemId(bankAccount.getPlaidItem() != null ? bankAccount.getPlaidItem().getId() : null);
        dto.setAccountMask(bankAccount.getAccountMask());
        dto.setManual(bankAccount.isManual());
        if (bankAccount.getPlaidItem() != null) {
            dto.setInstitutionName(bankAccount.getPlaidItem().getInstitutionName());
        }
        dto.setPlaidLinkedAt(bankAccount.getPlaidLinkedAt());
        return dto;
    }

    private BankAccount toEntity(BankAccountDTO bankAccountDTO) {
        if (bankAccountDTO == null) {
            return null;
        }
        BankAccount bankAccount = new BankAccount();
        bankAccount.setId(bankAccountDTO.getId());
        bankAccount.setName(bankAccountDTO.getName());
        bankAccount.setCurrentBalance(bankAccountDTO.getCurrentBalance());

        if (bankAccountDTO.getAccountType() != null) {
            bankAccount.setAccountType(AccountType.valueOf(bankAccountDTO.getAccountType()));
        }

        if (bankAccountDTO.getAppUserId() != null) {
            if (!appUserRepository.existsById(bankAccountDTO.getAppUserId())) {
                throw new EntityNotFoundException("AppUser not found with id: " + bankAccountDTO.getAppUserId());
            }
            bankAccount.setAppUser(appUserRepository.getReferenceById(bankAccountDTO.getAppUserId()));
        }

        return bankAccount;
    }

    public boolean isCreditCard(UUID accountId) {
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + accountId));
        return account.getAccountType() == AccountType.CREDIT_CARD;
    }

    public BankAccount findEntityById(UUID id) {
        return bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + id));
    }

    public java.util.Optional<BankAccount> findByPlaidAccountId(String plaidAccountId) {
        return bankAccountRepository.findByPlaidAccountId(plaidAccountId);
    }

    public List<BankAccountDTO> getByPlaidItemId(UUID plaidItemId) {
        return bankAccountRepository.findByPlaidItemId(plaidItemId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public BankAccountDTO createPlaidAccount(BankAccountDTO dto, com.budget.budgetai.model.PlaidItem plaidItem,
            String plaidAccountId, String mask) {
        BankAccount account = toEntity(dto);
        account.setPlaidItem(plaidItem);
        account.setPlaidAccountId(plaidAccountId);
        account.setAccountMask(mask);
        account.setManual(false);
        account.setPlaidLinkedAt(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC));
        BankAccount saved = bankAccountRepository.save(account);

        if (saved.getCurrentBalance().compareTo(java.math.BigDecimal.ZERO) != 0) {
            java.math.BigDecimal initialAmount = saved.getAccountType() == AccountType.CREDIT_CARD
                    ? saved.getCurrentBalance().negate()
                    : saved.getCurrentBalance();
            createAuditTransaction(saved, initialAmount, "Initial Balance (Plaid)");
        }

        if (saved.getAccountType() == AccountType.CREDIT_CARD) {
            createCCPaymentEnvelope(saved);
        }

        return toDTO(saved);
    }

    public BankAccountDTO linkPlaidAccount(UUID existingAccountId, UUID userId,
            com.budget.budgetai.model.PlaidItem plaidItem,
            String plaidAccountId, String mask, java.math.BigDecimal plaidBalance) {
        BankAccount account = bankAccountRepository.findById(existingAccountId)
                .orElseThrow(() -> new EntityNotFoundException("BankAccount not found with id: " + existingAccountId));

        if (!account.getAppUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to link this account");
        }

        BigDecimal oldBalance = account.getCurrentBalance();

        account.setPlaidItem(plaidItem);
        account.setPlaidAccountId(plaidAccountId);
        account.setAccountMask(mask);
        account.setManual(false);
        account.setCurrentBalance(plaidBalance);
        account.setPlaidLinkedAt(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC));
        BankAccount saved = bankAccountRepository.save(account);

        // Create an audit transaction for the balance difference so that the
        // transaction-calculated balance stays consistent for previously-manual
        // accounts.
        BigDecimal difference = plaidBalance.subtract(oldBalance);
        if (difference.compareTo(java.math.BigDecimal.ZERO) != 0) {
            createAuditTransaction(saved, difference, "Balance Adjustment (Plaid Link)");
        }

        return toDTO(saved);
    }
}
