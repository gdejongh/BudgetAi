package com.budget.budgetai.service;

import com.budget.budgetai.dto.TransactionDTO;
import com.budget.budgetai.model.Transaction;
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
    private final EnvelopeService envelopeService;

    public TransactionService(TransactionRepository transactionRepository, AppUserRepository appUserRepository,
                              BankAccountRepository bankAccountRepository, EnvelopeRepository envelopeRepository,
                              BankAccountService bankAccountService, EnvelopeService envelopeService) {
        this.transactionRepository = transactionRepository;
        this.appUserRepository = appUserRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.envelopeRepository = envelopeRepository;
        this.bankAccountService = bankAccountService;
        this.envelopeService = envelopeService;
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
        bankAccountService.updateBalance(transaction.getBankAccount().getId(), transaction.getAmount());
        if (transaction.getEnvelope() != null) {
            envelopeService.updateBalance(transaction.getEnvelope().getId(), transaction.getAmount());
        }
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

    public List<TransactionDTO> getByAppUserIdAndTransactionDateBetween(UUID appUserId, LocalDate startDate, LocalDate endDate) {
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
        UUID newBankAccountId = transactionDTO.getBankAccountId() != null ? transactionDTO.getBankAccountId() : oldBankAccountId;

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

        adjustBalances(oldBankAccountId, newBankAccountId, oldEnvelopeId, newEnvelopeId, oldAmount, newAmount);

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

        UUID oldBankAccountId = transaction.getBankAccount() != null ? transaction.getBankAccount().getId() : null;
        UUID oldEnvelopeId = transaction.getEnvelope() != null ? transaction.getEnvelope().getId() : null;

        adjustBalances(
                oldBankAccountId, null,
                oldEnvelopeId, null,
                transaction.getAmount(), BigDecimal.ZERO
        );

        transactionRepository.delete(transaction);
    }

    private void adjustBalances(UUID oldAccountId, UUID newAccountId,
                                UUID oldEnvelopeId, UUID newEnvelopeId,
                                BigDecimal oldAmount, BigDecimal newAmount) {

        BigDecimal amountDifference = newAmount.subtract(oldAmount);

        // --- Bank Account Logic ---
        if (oldAccountId != null && newAccountId == null) {
            // Revert (used for delete)
            bankAccountService.updateBalance(oldAccountId, oldAmount.negate());
        } else if (oldAccountId == null && newAccountId != null) {
            // Apply new (used if a transaction previously lacked an account)
            bankAccountService.updateBalance(newAccountId, newAmount);
        } else if (oldAccountId != null) { // both are not null
            if (!oldAccountId.equals(newAccountId)) {
                // Account changed
                bankAccountService.updateBalance(oldAccountId, oldAmount.negate());
                bankAccountService.updateBalance(newAccountId, newAmount);
            } else if (amountDifference.compareTo(BigDecimal.ZERO) != 0) {
                // Same account, apply difference
                bankAccountService.updateBalance(oldAccountId, amountDifference);
            }
        }

        // --- Envelope Logic ---
        if (oldEnvelopeId != null && newEnvelopeId == null) {
            // Revert (used for delete or envelope removal)
            envelopeService.updateBalance(oldEnvelopeId, oldAmount.negate());
        } else if (oldEnvelopeId == null && newEnvelopeId != null) {
            // Apply new (used for envelope addition)
            envelopeService.updateBalance(newEnvelopeId, newAmount);
        } else if (oldEnvelopeId != null) { // both are not null
            if (!oldEnvelopeId.equals(newEnvelopeId)) {
                // Envelope changed
                envelopeService.updateBalance(oldEnvelopeId, oldAmount.negate());
                envelopeService.updateBalance(newEnvelopeId, newAmount);
            } else if (amountDifference.compareTo(BigDecimal.ZERO) != 0) {
                // Same envelope, apply difference
                envelopeService.updateBalance(oldEnvelopeId, amountDifference);
            }
        }
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
                transaction.getCreatedAt()
        );
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

        if (transactionDTO.getAppUserId() != null) {
            if (!appUserRepository.existsById(transactionDTO.getAppUserId())) {
                throw new EntityNotFoundException("AppUser not found with id: " + transactionDTO.getAppUserId());
            }
            transaction.setAppUser(appUserRepository.getReferenceById(transactionDTO.getAppUserId()));
        }

        if (transactionDTO.getBankAccountId() != null) {
            if (!bankAccountRepository.existsById(transactionDTO.getBankAccountId())) {
                throw new EntityNotFoundException("BankAccount not found with id: " + transactionDTO.getBankAccountId());
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
