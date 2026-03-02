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
            createAuditTransaction(savedAccount, savedAccount.getCurrentBalance(), "Initial Balance");
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

        bankAccount.setName(bankAccountDTO.getName());
        bankAccount.setCurrentBalance(bankAccountDTO.getCurrentBalance());
        BankAccount updatedAccount = bankAccountRepository.save(bankAccount);

        BigDecimal difference = bankAccountDTO.getCurrentBalance().subtract(oldBalance);
        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            createAuditTransaction(updatedAccount, difference, "Adjustment");
        }

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
        return new BankAccountDTO(
                bankAccount.getId(),
                bankAccount.getAppUser().getId(),
                bankAccount.getName(),
                bankAccount.getAccountType() != null ? bankAccount.getAccountType().name()
                        : AccountType.CHECKING.name(),
                bankAccount.getCurrentBalance(),
                bankAccount.getCreatedAt());
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
}
