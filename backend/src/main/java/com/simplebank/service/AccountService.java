package com.simplebank.service;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.TransactionResponse;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.Account;
import com.simplebank.model.AccountType;
import com.simplebank.model.Transaction;
import com.simplebank.model.TransactionType;
import com.simplebank.model.User;
import com.simplebank.repository.AccountRepository;
import com.simplebank.repository.TransactionRepository;
import com.simplebank.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * All banking business rules live here (project doc, section 6):
 *   1. Cannot withdraw more than the balance
 *   2. Deposit (and withdraw) amounts must be positive
 *   3. Every deposit/withdrawal is recorded as a transaction
 * Plus: the balance can't be edited directly, and only empty accounts can be deleted.
 */
@Service
public class AccountService {

    /** Largest value a DECIMAL(10,2) column can hold. */
    static final BigDecimal MAX_BALANCE = new BigDecimal("99999999.99");

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    // ----- Create -----

    public AccountResponse createAccount(Long userId, AccountType accountType) {
        User user = findUser(userId);
        Account account = accountRepository.save(new Account(userId, accountType));
        return AccountResponse.from(account, user);
    }

    // ----- Read -----

    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(account -> AccountResponse.from(account, findUser(account.getUserId())))
                .toList();
    }

    public AccountResponse getAccount(Long accountId) {
        Account account = findAccount(accountId);
        return AccountResponse.from(account, findUser(account.getUserId()));
    }

    /** One user -> many accounts. 404 if the user doesn't exist. */
    public List<AccountResponse> getAccountsForUser(Long userId) {
        User user = findUser(userId);
        return accountRepository.findByUserId(userId).stream()
                .map(account -> AccountResponse.from(account, user))
                .toList();
    }

    public List<TransactionResponse> getTransactions(Long accountId) {
        findAccount(accountId); // 404 for unknown accounts instead of an empty list
        return transactionRepository.findByAccountIdOrderByTxnIdDesc(accountId).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    // ----- Update -----

    /** Only the account type can be changed. Money only moves through deposit/withdraw. */
    public AccountResponse updateAccount(Long accountId, AccountType accountType) {
        Account account = findAccount(accountId);
        account.setAccountType(accountType);
        accountRepository.save(account);
        return AccountResponse.from(account, findUser(account.getUserId()));
    }

    /*
     * deposit and withdraw are synchronized so two requests can't read the same
     * balance and overwrite each other's update. In Step 2 this is replaced by
     * @Transactional plus a database row lock.
     */
    public synchronized AccountResponse deposit(Long accountId, BigDecimal amount) {
        BigDecimal validAmount = validateAmount(amount);
        Account account = findAccount(accountId);

        BigDecimal newBalance = account.getBalance().add(validAmount);
        if (newBalance.compareTo(MAX_BALANCE) > 0) {
            throw new InvalidAmountException("Deposit would exceed the maximum balance of " + MAX_BALANCE);
        }

        account.setBalance(newBalance);
        accountRepository.save(account);
        transactionRepository.save(new Transaction(accountId, TransactionType.DEPOSIT, validAmount));
        return AccountResponse.from(account, findUser(account.getUserId()));
    }

    public synchronized AccountResponse withdraw(Long accountId, BigDecimal amount) {
        BigDecimal validAmount = validateAmount(amount);
        Account account = findAccount(accountId);

        if (account.getBalance().compareTo(validAmount) < 0) {
            throw new InsufficientFundsException(account.getBalance(), validAmount);
        }

        account.setBalance(account.getBalance().subtract(validAmount));
        accountRepository.save(account);
        transactionRepository.save(new Transaction(accountId, TransactionType.WITHDRAW, validAmount));
        return AccountResponse.from(account, findUser(account.getUserId()));
    }

    // ----- Delete -----

    /** Only empty accounts can be deleted, so money never disappears. Its transactions go with it. */
    public synchronized void deleteAccount(Long accountId) {
        Account account = findAccount(accountId);
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new OperationNotAllowedException("Account " + accountId + " has a balance of "
                    + account.getBalance() + ". Withdraw the full balance before deleting it.");
        }
        transactionRepository.deleteByAccountId(accountId);
        accountRepository.deleteById(accountId);
    }

    // ----- Helpers -----

    /** Returns the amount with exactly 2 decimal places, or throws if it breaks a rule. */
    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount cannot have more than 2 decimal places");
        }
        if (amount.compareTo(MAX_BALANCE) > 0) {
            throw new InvalidAmountException("Amount cannot exceed " + MAX_BALANCE);
        }
        return amount.setScale(2);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
