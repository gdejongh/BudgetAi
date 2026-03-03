package com.budget.budgetai.service;

import com.budget.budgetai.dto.CCPaymentRequest;
import com.budget.budgetai.dto.TransactionDTO;
import com.budget.budgetai.dto.TransferRequest;
import com.budget.budgetai.model.Envelope;
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
import java.util.Map;
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
    private final EnvelopeAllocationService envelopeAllocationService;

    public TransactionService(TransactionRepository transactionRepository, AppUserRepository appUserRepository,
            BankAccountRepository bankAccountRepository, EnvelopeRepository envelopeRepository,
            BankAccountService bankAccountService,
            EnvelopeAllocationService envelopeAllocationService) {
        this.transactionRepository = transactionRepository;
        this.appUserRepository = appUserRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.envelopeRepository = envelopeRepository;
        this.bankAccountService = bankAccountService;
        this.envelopeAllocationService = envelopeAllocationService;
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

        // Ownership check: bank account must belong to this user
        bankAccountRepository.findById(transactionDTO.getBankAccountId()).ifPresent(account -> {
            if (!account.getAppUser().getId().equals(transactionDTO.getAppUserId())) {
                throw new IllegalArgumentException("Bank account does not belong to this user");
            }
        });

        // Ownership check: envelope must belong to this user
        if (transactionDTO.getEnvelopeId() != null) {
            envelopeRepository.findById(transactionDTO.getEnvelopeId()).ifPresent(env -> {
                if (!env.getAppUser().getId().equals(transactionDTO.getAppUserId())) {
                    throw new IllegalArgumentException("Envelope does not belong to this user");
                }
            });
        }

        Transaction transaction = toEntity(transactionDTO);
        // For credit cards, invert the balance update: positive balance = debt owed
        // A purchase (negative amount) increases debt; a refund (positive amount)
        // decreases debt
        BigDecimal balanceUpdate = isCreditCard(transaction.getBankAccount().getId())
                ? transaction.getAmount().negate()
                : transaction.getAmount();
        bankAccountService.updateBalance(transaction.getBankAccount().getId(), balanceUpdate);

        // Auto-move: when a CC purchase is assigned to an envelope,
        // increase the CC Payment envelope's allocation by the full purchase amount
        if (isCreditCard(transaction.getBankAccount().getId())
                && transaction.getEnvelope() != null
                && transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            autoMoveToCCPaymentEnvelope(transaction);
        }

        // Reverse flow for CC refunds: when a refund is assigned to an envelope,
        // decrease the CC Payment envelope's allocation (less cash needed to pay the
        // card)
        if (isCreditCard(transaction.getBankAccount().getId())
                && transaction.getEnvelope() != null
                && transaction.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            reverseRefundFromCCPaymentEnvelope(transaction);
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
        if (transactionDTO.getAmount() == null) {
            throw new IllegalArgumentException("Transaction amount cannot be null");
        }
        if (transactionDTO.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date cannot be null");
        }

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));

        UUID transactionUserId = transaction.getAppUser().getId();

        UUID oldBankAccountId = transaction.getBankAccount() != null ? transaction.getBankAccount().getId() : null;
        UUID newBankAccountId = transactionDTO.getBankAccountId() != null ? transactionDTO.getBankAccountId()
                : oldBankAccountId;

        // Ownership check: new bank account must belong to this user
        if (newBankAccountId != null && !newBankAccountId.equals(oldBankAccountId)) {
            bankAccountRepository.findById(newBankAccountId).ifPresent(account -> {
                if (!account.getAppUser().getId().equals(transactionUserId)) {
                    throw new IllegalArgumentException("Bank account does not belong to this user");
                }
            });
        }

        // Ownership check: new envelope must belong to this user
        UUID newEnvelopeIdForCheck = transactionDTO.getEnvelopeId();
        UUID oldEnvelopeIdForCheck = transaction.getEnvelope() != null ? transaction.getEnvelope().getId() : null;
        if (newEnvelopeIdForCheck != null && !newEnvelopeIdForCheck.equals(oldEnvelopeIdForCheck)) {
            envelopeRepository.findById(newEnvelopeIdForCheck).ifPresent(env -> {
                if (!env.getAppUser().getId().equals(transactionUserId)) {
                    throw new IllegalArgumentException("Envelope does not belong to this user");
                }
            });
        }

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

        // --- CC Payment envelope auto-move adjustments on update ---
        boolean oldWasCCPurchaseWithEnvelope = oldBankAccountId != null
                && isCreditCard(oldBankAccountId)
                && oldEnvelopeId != null
                && oldAmount.compareTo(BigDecimal.ZERO) < 0;

        boolean newIsCCPurchaseWithEnvelope = newBankAccountId != null
                && isCreditCard(newBankAccountId)
                && newEnvelopeId != null
                && newAmount.compareTo(BigDecimal.ZERO) < 0;

        boolean oldWasCCRefundWithEnvelope = oldBankAccountId != null
                && isCreditCard(oldBankAccountId)
                && oldEnvelopeId != null
                && oldAmount.compareTo(BigDecimal.ZERO) > 0;

        boolean newIsCCRefundWithEnvelope = newBankAccountId != null
                && isCreditCard(newBankAccountId)
                && newEnvelopeId != null
                && newAmount.compareTo(BigDecimal.ZERO) > 0;

        LocalDate oldTransactionMonth = transaction.getTransactionDate().withDayOfMonth(1);
        LocalDate newTransactionMonth = transactionDTO.getTransactionDate() != null
                ? transactionDTO.getTransactionDate().withDayOfMonth(1)
                : oldTransactionMonth;

        // Reverse old auto-move for purchases
        if (oldWasCCPurchaseWithEnvelope) {
            envelopeRepository.findByLinkedAccountId(oldBankAccountId).ifPresent(ccEnv -> {
                envelopeAllocationService.addToAllocation(ccEnv.getId(), oldTransactionMonth, oldAmount.abs().negate());
            });
        }

        // Reverse old refund-move (add back what was subtracted)
        if (oldWasCCRefundWithEnvelope) {
            envelopeRepository.findByLinkedAccountId(oldBankAccountId).ifPresent(ccEnv -> {
                envelopeAllocationService.addToAllocation(ccEnv.getId(), oldTransactionMonth, oldAmount);
            });
        }

        // Apply new auto-move for purchases (full amount, regardless of envelope
        // remaining)
        if (newIsCCPurchaseWithEnvelope) {
            envelopeRepository.findByLinkedAccountId(newBankAccountId).ifPresent(ccEnv -> {
                envelopeAllocationService.addToAllocation(ccEnv.getId(), newTransactionMonth, newAmount.abs());
            });
        }

        // Apply new refund-move (subtract from CC Payment envelope)
        if (newIsCCRefundWithEnvelope) {
            envelopeRepository.findByLinkedAccountId(newBankAccountId).ifPresent(ccEnv -> {
                envelopeAllocationService.addToAllocation(ccEnv.getId(), newTransactionMonth, newAmount.negate());
            });
        }

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

        // If this is a CC_PAYMENT or TRANSFER, also delete the linked transaction and
        // revert its
        // balance
        if ((transaction.getTransactionType() == TransactionType.CC_PAYMENT
                || transaction.getTransactionType() == TransactionType.TRANSFER)
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

        // Reverse auto-move if this was a CC purchase assigned to an envelope
        if (oldBankAccountId != null && isCreditCard(oldBankAccountId)
                && transaction.getEnvelope() != null
                && transaction.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            reverseAutoMove(transaction);
        }

        // Reverse refund-move if this was a CC refund assigned to an envelope
        if (oldBankAccountId != null && isCreditCard(oldBankAccountId)
                && transaction.getEnvelope() != null
                && transaction.getAmount().compareTo(BigDecimal.ZERO) > 0
                && transaction.getTransactionType() != TransactionType.CC_PAYMENT) {
            // Refund deletion: add back what was subtracted from CC Payment envelope
            UUID ccAccountId = transaction.getBankAccount().getId();
            envelopeRepository.findByLinkedAccountId(ccAccountId).ifPresent(ccEnv -> {
                LocalDate transactionMonth = transaction.getTransactionDate().withDayOfMonth(1);
                envelopeAllocationService.addToAllocation(ccEnv.getId(), transactionMonth, transaction.getAmount());
            });
        }

        transactionRepository.delete(transaction);
    }

    /**
     * Auto-move funds to the CC Payment envelope when a CC purchase is assigned to
     * an envelope.
     * The full purchase amount is moved (not just the covered portion), matching
     * YNAB behavior:
     * the CC Payment envelope always reflects total assigned spending on the card.
     * If the spending envelope is overspent, it goes negative but the CC Payment
     * envelope still receives the full amount.
     */
    private void autoMoveToCCPaymentEnvelope(Transaction transaction) {
        UUID ccAccountId = transaction.getBankAccount().getId();
        Envelope ccPaymentEnvelope = envelopeRepository.findByLinkedAccountId(ccAccountId).orElse(null);
        if (ccPaymentEnvelope == null) {
            return; // No CC Payment envelope found — skip
        }

        // Purchase amount is negative; take absolute value
        BigDecimal purchaseAmount = transaction.getAmount().abs();

        // Increase the CC Payment envelope's allocation for the transaction's month
        LocalDate transactionMonth = transaction.getTransactionDate().withDayOfMonth(1);
        envelopeAllocationService.addToAllocation(ccPaymentEnvelope.getId(), transactionMonth, purchaseAmount);
    }

    /**
     * Reverse flow for CC refunds: when a refund is assigned to an envelope,
     * decrease the CC Payment envelope's allocation since less cash is needed to
     * pay the card.
     */
    private void reverseRefundFromCCPaymentEnvelope(Transaction transaction) {
        UUID ccAccountId = transaction.getBankAccount().getId();
        Envelope ccPaymentEnvelope = envelopeRepository.findByLinkedAccountId(ccAccountId).orElse(null);
        if (ccPaymentEnvelope == null) {
            return;
        }

        BigDecimal refundAmount = transaction.getAmount(); // positive
        LocalDate transactionMonth = transaction.getTransactionDate().withDayOfMonth(1);
        envelopeAllocationService.addToAllocation(ccPaymentEnvelope.getId(), transactionMonth, refundAmount.negate());
    }

    /**
     * Reverse the auto-move when a CC purchase is deleted.
     * Subtracts the full |amount| from the CC Payment envelope — symmetric with
     * autoMoveToCCPaymentEnvelope which adds the full |amount|.
     */
    private void reverseAutoMove(Transaction transaction) {
        UUID ccAccountId = transaction.getBankAccount().getId();
        Envelope ccPaymentEnvelope = envelopeRepository.findByLinkedAccountId(ccAccountId).orElse(null);
        if (ccPaymentEnvelope == null) {
            return;
        }

        BigDecimal purchaseAmount = transaction.getAmount().abs();
        LocalDate transactionMonth = transaction.getTransactionDate().withDayOfMonth(1);
        envelopeAllocationService.addToAllocation(ccPaymentEnvelope.getId(), transactionMonth, purchaseAmount.negate());
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

        // Decrease CC Payment envelope allocation by the payment amount
        envelopeRepository.findByLinkedAccountId(request.getCreditCardId()).ifPresent(ccPaymentEnvelope -> {
            LocalDate paymentMonth = request.getTransactionDate().withDayOfMonth(1);
            envelopeAllocationService.addToAllocation(ccPaymentEnvelope.getId(), paymentMonth,
                    request.getAmount().negate());
        });

        return toDTO(savedBankTxn);
    }

    /**
     * Creates an account-to-account transfer — two linked transactions:
     * 1. Source side: negative amount (money leaves source)
     * 2. Destination side: positive amount (money enters destination)
     * No envelope assignments — transfers don't affect budgets.
     * Returns both DTOs: source first, destination second.
     */
    public List<TransactionDTO> createTransfer(TransferRequest request, UUID appUserId) {
        if (request == null) {
            throw new IllegalArgumentException("TransferRequest cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        // Ownership checks
        bankAccountRepository.findById(request.getSourceAccountId()).ifPresent(account -> {
            if (!account.getAppUser().getId().equals(appUserId)) {
                throw new IllegalArgumentException("Source account does not belong to this user");
            }
        });
        bankAccountRepository.findById(request.getDestinationAccountId()).ifPresent(account -> {
            if (!account.getAppUser().getId().equals(appUserId)) {
                throw new IllegalArgumentException("Destination account does not belong to this user");
            }
        });

        String sourceName = bankAccountRepository.findById(request.getSourceAccountId())
                .map(a -> a.getName()).orElse("Unknown");
        String destName = bankAccountRepository.findById(request.getDestinationAccountId())
                .map(a -> a.getName()).orElse("Unknown");

        String description = request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription()
                : null;

        // Source side: money leaves the source account
        Transaction sourceTxn = new Transaction();
        sourceTxn.setAppUser(appUserRepository.getReferenceById(appUserId));
        sourceTxn.setBankAccount(bankAccountRepository.getReferenceById(request.getSourceAccountId()));
        sourceTxn.setAmount(request.getAmount().negate());
        sourceTxn.setDescription(description);
        sourceTxn.setMerchantName("Transfer \u2192 " + destName);
        sourceTxn.setTransactionDate(request.getTransactionDate());
        sourceTxn.setTransactionType(TransactionType.TRANSFER);
        Transaction savedSourceTxn = transactionRepository.save(sourceTxn);

        // Destination side: money enters the destination account
        Transaction destTxn = new Transaction();
        destTxn.setAppUser(appUserRepository.getReferenceById(appUserId));
        destTxn.setBankAccount(bankAccountRepository.getReferenceById(request.getDestinationAccountId()));
        destTxn.setAmount(request.getAmount());
        destTxn.setDescription(description);
        destTxn.setMerchantName("Transfer from " + sourceName);
        destTxn.setTransactionDate(request.getTransactionDate());
        destTxn.setTransactionType(TransactionType.TRANSFER);
        Transaction savedDestTxn = transactionRepository.save(destTxn);

        // Link them bidirectionally
        savedSourceTxn.setLinkedTransaction(savedDestTxn);
        savedDestTxn.setLinkedTransaction(savedSourceTxn);
        transactionRepository.save(savedSourceTxn);
        transactionRepository.save(savedDestTxn);

        // Update balances — handle CC accounts with inversion
        BigDecimal sourceBalanceUpdate = isCreditCard(request.getSourceAccountId())
                ? request.getAmount() // CC: negative amount means debt decreases (negate of negate)
                : request.getAmount().negate();
        bankAccountService.updateBalance(request.getSourceAccountId(), sourceBalanceUpdate);

        BigDecimal destBalanceUpdate = isCreditCard(request.getDestinationAccountId())
                ? request.getAmount().negate() // CC: positive amount means debt increases
                : request.getAmount();
        bankAccountService.updateBalance(request.getDestinationAccountId(), destBalanceUpdate);

        return List.of(toDTO(savedSourceTxn), toDTO(savedDestTxn));
    }

    private boolean isCreditCard(UUID accountId) {
        return bankAccountService.isCreditCard(accountId);
    }

    // Mapper methods
    private TransactionDTO toDTO(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        TransactionDTO dto = new TransactionDTO(
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
        dto.setPending(transaction.isPending());
        dto.setMerchantName(transaction.getMerchantName());
        dto.setPlaidCategory(transaction.getPlaidCategory());
        dto.setPlaidTransactionId(transaction.getPlaidTransactionId());
        return dto;
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
