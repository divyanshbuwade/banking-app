package com.banking.accountsservice.service;

import com.banking.accountsservice.dto.AccountResponse;
import com.banking.accountsservice.dto.CreateAccountRequest;
import com.banking.accountsservice.entity.Account;
import com.banking.accountsservice.entity.AccountStatus;
import com.banking.accountsservice.entity.AccountType;
import com.banking.accountsservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("creating account for {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account already exists for email: " + request.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTranscationLimit(
                request.getAccountType() == AccountType.SAVINGS
                        ? new BigDecimal("10000")
                        : new BigDecimal("50000")
        );

        Account savedAccount = accountRepository.save(account);
        log.info("account created: {}", savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);
    }

    public AccountResponse getAccount(String accountNumber) {
        return mapToResponse(findAccount(accountNumber));
    }

    public java.util.List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public BigDecimal getBalance(String accountNumber) {
        return findAccount(accountNumber).getBalance();
    }
/*
* Block account - called by fraud detection services
* via kafka
* */
    @Transactional
    public void blockAccount(String accountNumber) {
        Account account = findAccount(accountNumber);
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("account blocked: {}", accountNumber);
    }

    /*
    * deuduct balance from sender  account
    * accountnumber
    * amount
    * */
    @Transactional
    public void deductBalance(String accountNumber, BigDecimal amount) {
        Account account = findActiveAccount(accountNumber);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance for account: " + accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("deducted {} from account {}", amount, accountNumber);
    }

    @Transactional
    public void creditBalance(String accountNumber, BigDecimal amount) {
        Account account = findActiveAccount(accountNumber);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("credited {} to account {}", amount, accountNumber);
    }

    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
    }

    private Account findActiveAccount(String accountNumber) {
        Account account = findAccount(accountNumber);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active: " + accountNumber);
        }
        return account;
    }

    // Generate unique 12 digit number

    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = SECURE_RANDOM.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setAccountNumber(account.getAccountNumber());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTranscationLimit(account.getDailyTranscationLimit());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }
}
