package com.budget.budgetai.service;

import com.budget.budgetai.dto.BankAccountDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.BankAccount;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.BankAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final AppUserRepository appUserRepository;

    public BankAccountService(BankAccountRepository bankAccountRepository, AppUserRepository appUserRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.appUserRepository = appUserRepository;
    }

    public BankAccountDTO create(BankAccountDTO bankAccountDTO) {
        BankAccount bankAccount = toEntity(bankAccountDTO);
        BankAccount savedAccount = bankAccountRepository.save(bankAccount);
        return toDTO(savedAccount);
    }

    public BankAccountDTO getById(UUID id) {
        return bankAccountRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
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
        Optional<BankAccount> existingAccount = bankAccountRepository.findById(id);
        if (existingAccount.isPresent()) {
            BankAccount bankAccount = existingAccount.get();
            bankAccount.setName(bankAccountDTO.getName());
            bankAccount.setCurrentBalance(bankAccountDTO.getCurrentBalance());
            BankAccount updatedAccount = bankAccountRepository.save(bankAccount);
            return toDTO(updatedAccount);
        }
        return null;
    }

    public boolean delete(UUID id) {
        if (bankAccountRepository.existsById(id)) {
            bankAccountRepository.deleteById(id);
            return true;
        }
        return false;
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
                bankAccount.getCurrentBalance(),
                bankAccount.getCreatedAt()
        );
    }

    private BankAccount toEntity(BankAccountDTO bankAccountDTO) {
        if (bankAccountDTO == null) {
            return null;
        }
        BankAccount bankAccount = new BankAccount();
        bankAccount.setId(bankAccountDTO.getId());
        bankAccount.setName(bankAccountDTO.getName());
        bankAccount.setCurrentBalance(bankAccountDTO.getCurrentBalance());

        if (bankAccountDTO.getAppUserId() != null) {
            Optional<AppUser> appUser = appUserRepository.findById(bankAccountDTO.getAppUserId());
            appUser.ifPresent(bankAccount::setAppUser);
        }

        return bankAccount;
    }
}
