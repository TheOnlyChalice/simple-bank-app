package com.simplebank.service;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.PageResponse;
import com.simplebank.dto.TransactionResponse;
import com.simplebank.dto.TransferResponse;
import com.simplebank.exception.AccountFrozenException;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.InvalidRequestException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.Account;
import com.simplebank.model.AccountType;
import com.simplebank.model.AuditAction;
import com.simplebank.model.Role;
import com.simplebank.model.Transaction;
import com.simplebank.model.TransactionType;
import com.simplebank.model.User;
import com.simplebank.repository.AccountFilter;
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
 * transfers move money between two accounts atomically, and no money moves into or
 * out of a frozen account.
 *
 * Every method that changes an account runs in a MongoDB transaction (transactions.run).
 * If two requests change the same account at once, MongoDB aborts one with a write
 * conflict, and it is retried with fresh data, so no update is ever lost.
 *
 * Every change is audited: successes inside the same transaction, and rejected or
 * failed attempts after it rolls back (see AuditService).
 */
@Service
public class AccountService {

    /** Largest balance or amount allowed (the old DECIMAL(10,2) limit). */
    static final BigDecimal MAX_BALANCE = new BigDecimal("99999999.99");

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final MongoTransactions transactions;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          MongoTransactions transactions,
                          AuditService auditService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.transactions = transactions;
        this.auditService = auditService;
    }

    // ----- Create -----

    public AccountResponse createAccount(Long userId, AccountType accountType) {
        AuditDetails audit = AuditDetails.forUser(userId).withDetails("accountType " + accountType);
        return auditService.recordFailures(AuditAction.ACCOUNT_CREATED, audit, () -> transactions.run(() -> {
            User user = findUser(userId);
            Account account = accountRepository.save(new Account(userId, accountType));
            auditService.recordSuccess(AuditAction.ACCOUNT_CREATED,
                    audit.withAccountId(account.getAccountId()), List.of());
            return AccountResponse.from(account, user);
        }));
    }

    // ----- Read -----

    /** One page of all accounts, oldest first. Pages are numbered from 0. */
    public PageResponse<AccountResponse> getAllAccounts(int page, int size) {
        return getAllAccounts(AccountFilter.NONE, page, size);
    }

    /** One page of accounts matching the filter (balance range and/or type), oldest first. */
    public PageResponse<AccountResponse> getAllAccounts(AccountFilter filter, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("accountId"));
        SearchRules.checkBalanceRange(filter.minBalance(), filter.maxBalance());
        return toResponses(accountRepository.search(filter, pageable));
    }

    /**
     * Premium accounts: every account whose balance is at or above the threshold,
     * richest first (accounts with equal balances, oldest first).
     */
    public PageResponse<AccountResponse> getPremiumAccounts(BigDecimal threshold, int page, int size) {
        if (threshold == null || threshold.signum() <= 0) {
            throw new InvalidRequestException("threshold must be greater than zero");
        }
        Pageable pageable = Paging.of(page, size,
                Sort.by(Sort.Direction.DESC, "balance").and(Sort.by("accountId")));
        return toResponses(accountRepository.search(new AccountFilter(threshold, null, null), pageable));
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
        AuditDetails audit = AuditDetails.forAccount(accountId);
        return auditService.recordFailures(AuditAction.ACCOUNT_UPDATED, audit, () -> transactions.run(() -> {
            Account account = findAccount(accountId);
            AccountType oldType = account.getAccountType();
            account.setAccountType(accountType);
            accountRepository.save(account);
            auditService.recordSuccess(AuditAction.ACCOUNT_UPDATED, audit
                    .withUserId(account.getUserId())
                    .withDetails("accountType " + oldType + " -> " + accountType), List.of());
            return AccountResponse.from(account, findUser(account.getUserId()));
        }));
    }

    // ----- Freeze -----

    /**
     * Freezing stops all money movement (deposits, withdrawals, and transfers in or out)
     * until the account is unfrozen. 'by' records who froze it: the customer or the bank.
     */
    public AccountResponse freezeAccount(Long accountId, Role by) {
        AuditDetails audit = AuditDetails.forAccount(accountId);
        return auditService.recordFailures(AuditAction.ACCOUNT_FROZEN, audit, () -> transactions.run(() -> {
            Account account = findAccount(accountId);
            if (account.isFrozen()) {
                throw new OperationNotAllowedException("Account " + accountId + " is already frozen.");
            }
            account.freeze(by);
            accountRepository.save(account);
            auditService.recordSuccess(AuditAction.ACCOUNT_FROZEN, audit
                    .withUserId(account.getUserId())
                    .withDetails("frozen by " + by), List.of());
            return AccountResponse.from(account, findUser(account.getUserId()));
        }));
    }

    /**
     * A customer can undo a freeze they made themselves. A freeze made by the bank (for
     * example, for suspected fraud) can only be lifted by bank staff, so someone who has
     * stolen the customer's password can't simply unfreeze the account.
     */
    public AccountResponse unfreezeAccount(Long accountId, Role by) {
        AuditDetails audit = AuditDetails.forAccount(accountId);
        return auditService.recordFailures(AuditAction.ACCOUNT_UNFROZEN, audit, () -> transactions.run(() -> {
            Account account = findAccount(accountId);
            if (!account.isFrozen()) {
                throw new OperationNotAllowedException("Account " + accountId + " is not frozen.");
            }
            if (account.getFrozenBy() == Role.ADMIN && by != Role.ADMIN) {
                throw new AccountFrozenException("Account " + accountId
                        + " was frozen by the bank. Contact the bank to have it unfrozen.");
            }
            Role frozenBy = account.getFrozenBy();
            account.unfreeze();
            accountRepository.save(account);
            auditService.recordSuccess(AuditAction.ACCOUNT_UNFROZEN, audit
                    .withUserId(account.getUserId())
                    .withDetails("unfrozen by " + by + " (was frozen by " + frozenBy + ")"), List.of());
            return AccountResponse.from(account, findUser(account.getUserId()));
        }));
    }

    // ----- Money -----

    public AccountResponse deposit(Long accountId, BigDecimal amount) {
        AuditDetails audit = AuditDetails.forAccount(accountId).withAmount(amount);
        return auditService.recordFailures(AuditAction.DEPOSIT, audit, () -> {
            BigDecimal validAmount = validateAmount(amount);
            return transactions.run(() -> {
                Account account = findAccount(accountId);
                requireNotFrozen(account);
                BigDecimal oldBalance = account.getBalance();

                BigDecimal newBalance = oldBalance.add(validAmount);
                if (newBalance.compareTo(MAX_BALANCE) > 0) {
                    throw new InvalidAmountException("Deposit would exceed the maximum balance of " + MAX_BALANCE);
                }

                account.setBalance(newBalance);
                accountRepository.save(account);
                Transaction txn = transactionRepository.save(
                        new Transaction(accountId, TransactionType.DEPOSIT, validAmount));
                auditService.recordSuccess(AuditAction.DEPOSIT, audit
                        .withUserId(account.getUserId())
                        .withAmount(validAmount)
                        .withDetails("balance " + oldBalance + " -> " + newBalance), List.of(txn.getTxnId()));
                return AccountResponse.from(account, findUser(account.getUserId()));
            });
        });
    }

    public AccountResponse withdraw(Long accountId, BigDecimal amount) {
        AuditDetails audit = AuditDetails.forAccount(accountId).withAmount(amount);
        return auditService.recordFailures(AuditAction.WITHDRAW, audit, () -> {
            BigDecimal validAmount = validateAmount(amount);
            return transactions.run(() -> {
                Account account = findAccount(accountId);
                requireNotFrozen(account);
                BigDecimal oldBalance = account.getBalance();

                if (oldBalance.compareTo(validAmount) < 0) {
                    throw new InsufficientFundsException(oldBalance, validAmount);
                }

                BigDecimal newBalance = oldBalance.subtract(validAmount);
                account.setBalance(newBalance);
                accountRepository.save(account);
                Transaction txn = transactionRepository.save(
                        new Transaction(accountId, TransactionType.WITHDRAW, validAmount));
                auditService.recordSuccess(AuditAction.WITHDRAW, audit
                        .withUserId(account.getUserId())
                        .withAmount(validAmount)
                        .withDetails("balance " + oldBalance + " -> " + newBalance), List.of(txn.getTxnId()));
                return AccountResponse.from(account, findUser(account.getUserId()));
            });
        });
    }

    // ----- Transfer -----

    /**
     * Moves money between two accounts in one transaction: both balances change, both
     * history documents and the audit event are written, or nothing happens at all.
     * MongoDB aborts conflicting transactions instead of making them wait, so deadlocks can't happen.
     */
    public TransferResponse transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        AuditDetails audit = AuditDetails.forTransfer(fromAccountId, toAccountId, amount);
        return auditService.recordFailures(AuditAction.TRANSFER, audit, () -> {
            checkTransferRequest(fromAccountId, toAccountId, amount);
            return transactions.run(() -> moveMoney(fromAccountId, toAccountId, amount, audit, null));
        });
    }

    /** The checks that don't need the database: different accounts, and a valid amount. */
    void checkTransferRequest(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidRequestException("Cannot transfer to the same account");
        }
        validateAmount(amount);
    }

    /**
     * The transfer itself. Must be called INSIDE a transaction (transactions.run): it is
     * shared by instant transfers and by ScheduledTransferService, which runs it in the same
     * transaction that marks the scheduled transfer as completed. 'note' is added to the
     * audit details, e.g. "scheduled transfer #12".
     */
    TransferResponse moveMoney(Long fromAccountId, Long toAccountId, BigDecimal amount,
                               AuditDetails audit, String note) {
        checkTransferRequest(fromAccountId, toAccountId, amount);
        BigDecimal validAmount = validateAmount(amount);

        Account from = findAccount(fromAccountId);
        Account to = findAccount(toAccountId);
        requireNotFrozen(from);
        requireNotFrozen(to);
        BigDecimal fromOldBalance = from.getBalance();
        BigDecimal toOldBalance = to.getBalance();

        if (fromOldBalance.compareTo(validAmount) < 0) {
            throw new InsufficientFundsException(fromOldBalance, validAmount);
        }
        BigDecimal newToBalance = toOldBalance.add(validAmount);
        if (newToBalance.compareTo(MAX_BALANCE) > 0) {
            throw new InvalidAmountException("Transfer would exceed the maximum balance of "
                    + MAX_BALANCE + " in account " + toAccountId);
        }

        from.setBalance(fromOldBalance.subtract(validAmount));
        to.setBalance(newToBalance);
        accountRepository.save(from);
        accountRepository.save(to);
        Transaction out = transactionRepository.save(
                new Transaction(fromAccountId, TransactionType.TRANSFER_OUT, validAmount, toAccountId));
        Transaction in = transactionRepository.save(
                new Transaction(toAccountId, TransactionType.TRANSFER_IN, validAmount, fromAccountId));

        String details = "from balance " + fromOldBalance + " -> " + from.getBalance()
                + "; to balance " + toOldBalance + " -> " + to.getBalance()
                + (note == null ? "" : "; " + note);
        auditService.recordSuccess(AuditAction.TRANSFER, audit
                .withUserId(from.getUserId())
                .withAmount(validAmount)
                .withDetails(details), List.of(out.getTxnId(), in.getTxnId()));

        return new TransferResponse(
                AccountResponse.from(from, findUser(from.getUserId())),
                AccountResponse.from(to, findUser(to.getUserId())),
                validAmount);
    }

    // ----- Delete -----

    /**
     * Only empty accounts can be deleted, so money never disappears. Its transaction
     * history goes with it, but the audit log keeps a permanent record of everything.
     */
    public void deleteAccount(Long accountId) {
        AuditDetails audit = AuditDetails.forAccount(accountId);
        auditService.recordFailures(AuditAction.ACCOUNT_DELETED, audit, () -> transactions.run(() -> {
            Account account = findAccount(accountId);
            if (account.isFrozen()) {
                throw new AccountFrozenException("Account " + accountId + " is frozen. Unfreeze it before closing it.");
            }
            if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new OperationNotAllowedException("Account " + accountId + " has a balance of "
                        + account.getBalance() + ". Withdraw the full balance before deleting it.");
            }
            transactionRepository.deleteByAccountId(accountId);
            accountRepository.delete(account);
            auditService.recordSuccess(AuditAction.ACCOUNT_DELETED, audit
                    .withUserId(account.getUserId())
                    .withDetails("accountType " + account.getAccountType()), List.of());
            return null;
        }));
    }

    // ----- Helpers -----

    /** Adds each account's owner, loading all the owners in one query (avoids "N+1 queries"). */
    private PageResponse<AccountResponse> toResponses(Page<Account> accounts) {
        Set<Long> userIds = accounts.stream().map(Account::getUserId).collect(Collectors.toSet());
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        return PageResponse.from(
                accounts.map(account -> AccountResponse.from(account, usersById.get(account.getUserId()))));
    }

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

    private void requireNotFrozen(Account account) {
        if (account.isFrozen()) {
            throw new AccountFrozenException(account.getAccountId());
        }
    }

    Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
