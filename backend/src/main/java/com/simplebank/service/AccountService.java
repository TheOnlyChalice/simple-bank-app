package com.simplebank.service;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.PageResponse;
import com.simplebank.dto.TransactionResponse;
import com.simplebank.dto.TransferResponse;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.InvalidRequestException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * All banking business rules live here (project doc, section 6):
 *   1. Cannot withdraw more than the balance
 *   2. Deposit (and withdraw) amounts must be positive
 *   3. Every deposit/withdrawal is recorded as a transaction
 * Plus: the balance can't be edited directly, only empty accounts can be deleted,
 * and transfers move money between two accounts atomically.
 *
 * Every method that changes an account runs in a MongoDB transaction (transactions.run).
 * If two requests change the same account at once, MongoDB aborts one with a write
 * conflict, and it is retried with fresh data, so no update is ever lost.
 */
@Service
public class AccountService {

    /** Largest balance or amount allowed (the old DECIMAL(10,2) limit). */
    static final BigDecimal MAX_BALANCE = new BigDecimal("99999999.99");

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final MongoTransactions transactions;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          MongoTransactions transactions) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.transactions = transactions;
    }

    // ----- Create -----

    public AccountResponse createAccount(Long userId, AccountType accountType) {
        User user = findUser(userId);
        Account account = accountRepository.save(new Account(userId, accountType));
        return AccountResponse.from(account, user);
    }

    // ----- Read -----

    /** One page of accounts, oldest first. Pages are numbered from 0. */
    public PageResponse<AccountResponse> getAllAccounts(int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("accountId"));
        Page<Account> accounts = accountRepository.findAll(pageable);

        // Load the owners of this page's accounts in one query, instead of one query
        // per account (avoids the "N+1 query" problem)
        Set<Long> userIds = accounts.stream().map(Account::getUserId).collect(Collectors.toSet());
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        return PageResponse.from(
                accounts.map(account -> AccountResponse.from(account, usersById.get(account.getUserId()))));
    }

    public AccountResponse getAccount(Long accountId) {
        Account account = findAccount(accountId);
        return AccountResponse.from(account, findUser(account.getUserId()));
    }

    /** One user -> many accounts. 404 if the user doesn't exist. */
    public List<AccountResponse> getAccountsForUser(Long userId) {
        User user = findUser(userId);
        return accountRepository.findByUserIdOrderByAccountIdAsc(userId).stream()
                .map(account -> AccountResponse.from(account, user))
                .toList();
    }

    /** One page of an account's history, newest first. Pages are numbered from 0. */
    public PageResponse<TransactionResponse> getTransactions(Long accountId, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "txnId"));
        findAccount(accountId); // 404 for unknown accounts instead of an empty page
        return PageResponse.from(
                transactionRepository.findByAccountId(accountId, pageable).map(TransactionResponse::from));
    }

    // ----- Update -----

    /** Only the account type can be changed. Money only moves through deposit, withdraw, and transfer. */
    public AccountResponse updateAccount(Long accountId, AccountType accountType) {
        return transactions.run(() -> {
            Account account = findAccount(accountId);
            account.setAccountType(accountType);
            accountRepository.save(account);
            return AccountResponse.from(account, findUser(account.getUserId()));
        });
    }

    public AccountResponse deposit(Long accountId, BigDecimal amount) {
        BigDecimal validAmount = validateAmount(amount);
        return transactions.run(() -> {
            Account account = findAccount(accountId);

            BigDecimal newBalance = account.getBalance().add(validAmount);
            if (newBalance.compareTo(MAX_BALANCE) > 0) {
                throw new InvalidAmountException("Deposit would exceed the maximum balance of " + MAX_BALANCE);
            }

            account.setBalance(newBalance);
            accountRepository.save(account);
            transactionRepository.save(new Transaction(accountId, TransactionType.DEPOSIT, validAmount));
            return AccountResponse.from(account, findUser(account.getUserId()));
        });
    }

    public AccountResponse withdraw(Long accountId, BigDecimal amount) {
        BigDecimal validAmount = validateAmount(amount);
        return transactions.run(() -> {
            Account account = findAccount(accountId);

            if (account.getBalance().compareTo(validAmount) < 0) {
                throw new InsufficientFundsException(account.getBalance(), validAmount);
            }

            account.setBalance(account.getBalance().subtract(validAmount));
            accountRepository.save(account);
            transactionRepository.save(new Transaction(accountId, TransactionType.WITHDRAW, validAmount));
            return AccountResponse.from(account, findUser(account.getUserId()));
        });
    }

    // ----- Transfer -----

    /**
     * Moves money between two accounts in one transaction: both balances change and
     * both history documents are written, or nothing happens at all. MongoDB aborts
     * conflicting transactions instead of making them wait, so deadlocks can't happen.
     */
    public TransferResponse transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidRequestException("Cannot transfer to the same account");
        }
        BigDecimal validAmount = validateAmount(amount);

        return transactions.run(() -> {
            Account from = findAccount(fromAccountId);
            Account to = findAccount(toAccountId);

            if (from.getBalance().compareTo(validAmount) < 0) {
                throw new InsufficientFundsException(from.getBalance(), validAmount);
            }
            BigDecimal newToBalance = to.getBalance().add(validAmount);
            if (newToBalance.compareTo(MAX_BALANCE) > 0) {
                throw new InvalidAmountException("Transfer would exceed the maximum balance of "
                        + MAX_BALANCE + " in account " + toAccountId);
            }

            from.setBalance(from.getBalance().subtract(validAmount));
            to.setBalance(newToBalance);
            accountRepository.save(from);
            accountRepository.save(to);
            transactionRepository.save(new Transaction(fromAccountId, TransactionType.TRANSFER_OUT, validAmount, toAccountId));
            transactionRepository.save(new Transaction(toAccountId, TransactionType.TRANSFER_IN, validAmount, fromAccountId));

            return new TransferResponse(
                    AccountResponse.from(from, findUser(from.getUserId())),
                    AccountResponse.from(to, findUser(to.getUserId())),
                    validAmount);
        });
    }

    // ----- Delete -----

    /** Only empty accounts can be deleted, so money never disappears. Its transactions go with it. */
    public void deleteAccount(Long accountId) {
        transactions.run(() -> {
            Account account = findAccount(accountId);
            if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new OperationNotAllowedException("Account " + accountId + " has a balance of "
                        + account.getBalance() + ". Withdraw the full balance before deleting it.");
            }
            transactionRepository.deleteByAccountId(accountId);
            accountRepository.delete(account);
            return null;
        });
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
