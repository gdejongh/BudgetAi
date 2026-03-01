package com.budget.budgetai.service;

import com.budget.budgetai.dto.CCPaymentRequest;
import com.budget.budgetai.dto.TransactionDTO;
import com.budget.budgetai.model.AccountType;
import com.budget.budgetai.model.Transaction;
import com.budget.budgetai.model.TransactionType;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
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
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AppUserRepository appUserRepository;
    private final BankAccountRepository bankAccountRepository;
    private final EnvelopeRepository envelopeRepository;
    private final BankAccountService bankAccountService;

    public TransactionService(TransactionRepository transactionRepository, AppUserRepository appUserRepository,
            BankAccountRepository bankAccountRepository, EnvelopeRepository envelopeRepository,
            BankAccountService bankAccountService) {
        this.transactionRepository = transactionRepository;
        this.appUserRepository = appUserRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.envelopeRepository = envelopeRepository;
        this.bankAccountService = bankAccountService;
    }

    public TransactionDTO create(TransactionDTO transactionDTO) {
        if (transactionDTO == null) {
            throw new IllegalArgumentException("TransactionDTO cannot be null");
        }
        if (transactionDTO.getAmount() == null) {
            throw new IllegalArgumentException("Transaction amount cannot be null");
        }
        if (transactionDTO.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date cannot be null");
        }
        if (transactionDTO.getAppUserId() == null) {
            throw new IllegalArgumentException("App user ID cannot be null");
        }
        if (transactionDTO.getBankAccountId() == null) {
            throw new IllegalArgumentException("Bank account ID cannot be null");
        }
        Transaction transaction = toEntity(transactionDTO);
        // For credit cards, invert the balance update: positive balance = debt owed
        // A purchase (negative amount) increases debt; a refund (positive amount)
        // decreases debt
        BigDecimal balanceUpdate = isCreditCard(transaction.getBankAccount().getId())
                ? transaction.getAmount().negate()
                : transaction.getAmount();
        bankAccountService.updateBalance(transaction.getBankAccount().getId(), balanceUpdate);
        Transaction savedTransaction = transactionRepository.save(transaction);
        return toDTO(savedTransaction);
    }

    public TransactionDTO getById(UUID id) {
        return transactionRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));
    }

    public List<TransactionDTO> getAll() {
        return transactionRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getByAppUserId(UUID appUserId) {
        return transactionRepository.findByAppUserId(appUserId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getByBankAccountId(UUID bankAccountId) {
        return transactionRepository.findByBankAccountId(bankAccountId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getByEnvelopeId(UUID envelopeId) {
        return transactionRepository.findByEnvelopeId(envelopeId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getByTransactionDateBetween(LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByTransactionDateBetween(startDate, endDate).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getByAppUserIdAndTransactionDateBetween(UUID appUserId, LocalDate startDate,
            LocalDate endDate) {
        return transactionRepository.findByAppUserIdAndTransactionDateBetween(appUserId, startDate, endDate).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public TransactionDTO update(UUID id, TransactionDTO transactionDTO) {
        if (transactionDTO == null) {
            throw new IllegalArgumentException("TransactionDTO cannot be null");
        }

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));

        UUID oldBankAccountId = transaction.getBankAccount() != null ? transaction.getBankAccount().getId() : null;
        UUID newBankAccountId = transactionDTO.getBankAccountId() != null ? transactionDTO.getBankAccountId()
                : oldBankAccountId;

        UUID oldEnvelopeId = transaction.getEnvelope() != null ? transaction.getEnvelope().getId() : null;
        UUID newEnvelopeId = transactionDTO.getEnvelopeId();

        BigDecimal oldAmount = transaction.getAmount();
        BigDecimal newAmount = transactionDTO.getAmount();

        if (newBankAccountId != null && !newBankAccountId.equals(oldBankAccountId)) {
            if (!bankAccountRepository.existsById(newBankAccountId)) {
                throw new EntityNotFoundException("BankAccount not found with id: " + newBankAccountId);
            }
        }
        if (newEnvelopeId != null && !newEnvelopeId.equals(oldEnvelopeId)) {
            if (!envelopeRepository.existsById(newEnvelopeId)) {
                throw new EntityNotFoundException("Envelope not found with id: " + newEnvelopeId);
            }
        }

        adjustBankAccountBalance(oldBankAccountId, newBankAccountId, oldAmount, newAmount);

        if (newBankAccountId != null && !newBankAccountId.equals(oldBankAccountId)) {
            transaction.setBankAccount(bankAccountRepository.getReferenceById(newBankAccountId));
        }

        if (newEnvelopeId == null) {
            transaction.setEnvelope(null);
        } else if (!newEnvelopeId.equals(oldEnvelopeId)) {
            transaction.setEnvelope(envelopeRepository.getReferenceById(newEnvelopeId));
        }

        transaction.setAmount(newAmount);
        transaction.setDescription(transactionDTO.getDescription());
        transaction.setTransactionDate(transactionDTO.getTransactionDate());

        return toDTO(transactionRepository.save(transaction));
    }

    public void delete(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));

        // If this is a CC_PAYMENT, also delete the linked transaction and revert its
        // balance
        if (transaction.getTransactionType() == TransactionType.CC_PAYMENT
                && transaction.getLinkedTransaction() != null) {
            Transaction linked = transaction.getLinkedTransaction();
            UUID linkedAccountId = linked.getBankAccount().getId();
            BigDecimal linkedRevert = isCreditCard(linkedAccountId)
                    ? linked.getAmount()
                    : linked.getAmount().negate();
            bankAccountService.updateBalance(linkedAccountId, linkedRevert);
            // Clear the mutual reference before deleting
            linked.setLinkedTransaction(null);
            transaction.setLinkedTransaction(null);
            transactionRepository.save(linked);
            transactionRepository.save(transaction);
            transactionRepository.delete(linked);
        }

        UUID oldBankAccountId = transaction.getBankAccount() != null ? transaction.getBankAccount().getId() : null;

        if (oldBankAccountId != null) {
            BigDecimal revertAmount = isCreditCard(oldBankAccountId)
                    ? transaction.getAmount()
                    : transaction.getAmount().negate();
            bankAccountService.updateBalance(oldBankAccountId, revertAmount);
        }

        transactionRepository.delete(transaction);
    }

    private void adjustBankAccountBalance(UUID oldAccountId, UUID newAccountId,
            BigDecimal oldAmount, BigDecimal newAmount) {

        BigDecimal amountDifference = newAmount.subtract(oldAmount);

        if (oldAccountId != null && newAccountId == null) {
            // Revert (used for delete)
            BigDecimal revert = isCreditCard(oldAccountId)
                    ? oldAmount
                    : oldAmount.negate();
            bankAccountService.updateBalance(oldAccountId, revert);
        } else if (oldAccountId == null && newAccountId != null) {
            // Apply new (used if a transaction previously lacked an account)
            BigDecimal apply = isCreditCard(newAccountId)
                    ? newAmount.negate()
                    : newAmount;
            bankAccountService.updateBalance(newAccountId, apply);
        } else if (oldAccountId != null) { // both are not null
            if (!oldAccountId.equals(newAccountId)) {
                // Account changed — revert old, apply new
                BigDecimal revertOld = isCreditCard(oldAccountId)
                        ? oldAmount
                        : oldAmount.negate();
                BigDecimal applyNew = isCreditCard(newAccountId)
                        ? newAmount.negate()
                        : newAmount;
                bankAccountService.updateBalance(oldAccountId, revertOld);
                bankAccountService.updateBalance(newAccountId, applyNew);
            } else if (amountDifference.compareTo(BigDecimal.ZERO) != 0) {
                // Same account, apply difference
                BigDecimal diff = isCreditCard(oldAccountId)
                        ? amountDifference.negate()
                        : amountDifference;
                bankAccountService.updateBalance(oldAccountId, diff);
            }
        }
    }

    /**
     * Creates a credit card payment — two linked transactions:
     * 1. Bank account side: negative amount (money leaves bank)
     * 2. Credit card side: negative amount (debt reduced, stored as positive
     * balance decrease)
     */
    public TransactionDTO createCCPayment(CCPaymentRequest request, UUID appUserId) {
        if (request == null) {
            throw new IllegalArgumentException("CCPaymentRequest cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (!bankAccountService.isCreditCard(request.getCreditCardId())) {
            throw new IllegalArgumentException("Target account is not a credit card");
        }
        if (bankAccountService.isCreditCard(request.getBankAccountId())) {
            throw new IllegalArgumentException("Source account cannot be a credit card");
        }

        String description = request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription()
                : "Credit Card Payment";

        // Bank side: money leaves the bank account (negative amount)
        Transaction bankTxn = new Transaction();
        bankTxn.setAppUser(appUserRepository.getReferenceById(appUserId));
        bankTxn.setBankAccount(bankAccountRepository.getReferenceById(request.getBankAccountId()));
        bankTxn.setAmount(request.getAmount().negate());
        bankTxn.setDescription(description);
        bankTxn.setTransactionDate(request.getTransactionDate());
        bankTxn.setTransactionType(TransactionType.CC_PAYMENT);
        Transaction savedBankTxn = transactionRepository.save(bankTxn);

        // CC side: debt is reduced (positive amount on the CC means debt decrease)
        Transaction ccTxn = new Transaction();
        ccTxn.setAppUser(appUserRepository.getReferenceById(appUserId));
        ccTxn.setBankAccount(bankAccountRepository.getReferenceById(request.getCreditCardId()));
        ccTxn.setAmount(request.getAmount());
        ccTxn.setDescription(description);
        ccTxn.setTransactionDate(request.getTransactionDate());
        ccTxn.setTransactionType(TransactionType.CC_PAYMENT);
        Transaction savedCcTxn = transactionRepository.save(ccTxn);

        // Link them together
        savedBankTxn.setLinkedTransaction(savedCcTxn);
        savedCcTxn.setLinkedTransaction(savedBankTxn);
        transactionRepository.save(savedBankTxn);
        transactionRepository.save(savedCcTxn);

        // Update balances:
        // Bank account: subtract the payment amount
        bankAccountService.updateBalance(request.getBankAccountId(), request.getAmount().negate());
        // Credit card: reduce debt (subtract from positive balance)
        bankAccountService.updateBalance(request.getCreditCardId(), request.getAmount().negate());

        return toDTO(savedBankTxn);
    }

    private boolean isCreditCard(UUID accountId) {
        return bankAccountService.isCreditCard(accountId);
    }

    // Mapper methods
    private TransactionDTO toDTO(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        return new TransactionDTO(
                transaction.getId(),
                transaction.getAppUser().getId(),
                transaction.getBankAccount().getId(),
                transaction.getEnvelope() != null ? transaction.getEnvelope().getId() : null,
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getTransactionType() != null ? transaction.getTransactionType().name()
                        : TransactionType.STANDARD.name(),
                transaction.getLinkedTransaction() != null ? transaction.getLinkedTransaction().getId() : null,
                transaction.getCreatedAt());
    }

    private Transaction toEntity(TransactionDTO transactionDTO) {
        if (transactionDTO == null) {
            return null;
        }
        Transaction transaction = new Transaction();
        transaction.setId(transactionDTO.getId());
        transaction.setAmount(transactionDTO.getAmount());
        transaction.setDescription(transactionDTO.getDescription());
        transaction.setTransactionDate(transactionDTO.getTransactionDate());

        if (transactionDTO.getTransactionType() != null) {
            transaction.setTransactionType(TransactionType.valueOf(transactionDTO.getTransactionType()));
        }

        if (transactionDTO.getAppUserId() != null) {
            if (!appUserRepository.existsById(transactionDTO.getAppUserId())) {
                throw new EntityNotFoundException("AppUser not found with id: " + transactionDTO.getAppUserId());
            }
            transaction.setAppUser(appUserRepository.getReferenceById(transactionDTO.getAppUserId()));
        }

        if (transactionDTO.getBankAccountId() != null) {
            if (!bankAccountRepository.existsById(transactionDTO.getBankAccountId())) {
                throw new EntityNotFoundException(
                        "BankAccount not found with id: " + transactionDTO.getBankAccountId());
            }
            transaction.setBankAccount(bankAccountRepository.getReferenceById(transactionDTO.getBankAccountId()));
        }

        if (transactionDTO.getEnvelopeId() != null) {
            if (!envelopeRepository.existsById(transactionDTO.getEnvelopeId())) {
                throw new EntityNotFoundException("Envelope not found with id: " + transactionDTO.getEnvelopeId());
            }
            transaction.setEnvelope(envelopeRepository.getReferenceById(transactionDTO.getEnvelopeId()));
        }

        return transaction;
    }
}
