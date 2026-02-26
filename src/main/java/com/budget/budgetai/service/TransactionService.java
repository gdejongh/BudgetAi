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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AppUserRepository appUserRepository;
    private final BankAccountRepository bankAccountRepository;
    private final EnvelopeRepository envelopeRepository;

    public TransactionService(TransactionRepository transactionRepository, AppUserRepository appUserRepository,
                              BankAccountRepository bankAccountRepository, EnvelopeRepository envelopeRepository) {
        this.transactionRepository = transactionRepository;
        this.appUserRepository = appUserRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.envelopeRepository = envelopeRepository;
    }

    public TransactionDTO create(TransactionDTO transactionDTO) {
        Transaction transaction = toEntity(transactionDTO);
        Transaction savedTransaction = transactionRepository.save(transaction);
        return toDTO(savedTransaction);
    }

    public TransactionDTO getById(UUID id) {
        return transactionRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
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
        Optional<Transaction> existingTransaction = transactionRepository.findById(id);
        if (existingTransaction.isPresent()) {
            Transaction transaction = existingTransaction.get();
            transaction.setAmount(transactionDTO.getAmount());
            transaction.setDescription(transactionDTO.getDescription());
            transaction.setTransactionDate(transactionDTO.getTransactionDate());

            if (transactionDTO.getBankAccountId() != null) {
                Optional<BankAccount> bankAccount = bankAccountRepository.findById(transactionDTO.getBankAccountId());
                bankAccount.ifPresent(transaction::setBankAccount);
            }

            if (transactionDTO.getEnvelopeId() != null) {
                Optional<Envelope> envelope = envelopeRepository.findById(transactionDTO.getEnvelopeId());
                envelope.ifPresent(transaction::setEnvelope);
            }

            Transaction updatedTransaction = transactionRepository.save(transaction);
            return toDTO(updatedTransaction);
        }
        return null;
    }

    public boolean delete(UUID id) {
        if (transactionRepository.existsById(id)) {
            transactionRepository.deleteById(id);
            return true;
        }
        return false;
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
            Optional<AppUser> appUser = appUserRepository.findById(transactionDTO.getAppUserId());
            appUser.ifPresent(transaction::setAppUser);
        }

        if (transactionDTO.getBankAccountId() != null) {
            Optional<BankAccount> bankAccount = bankAccountRepository.findById(transactionDTO.getBankAccountId());
            bankAccount.ifPresent(transaction::setBankAccount);
        }

        if (transactionDTO.getEnvelopeId() != null) {
            Optional<Envelope> envelope = envelopeRepository.findById(transactionDTO.getEnvelopeId());
            envelope.ifPresent(transaction::setEnvelope);
        }

        return transaction;
    }
}
