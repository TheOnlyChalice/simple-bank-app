package com.simplebank.service;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.TransactionResponse;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.AccountType;
import com.simplebank.model.TransactionType;
import com.simplebank.model.User;
import com.simplebank.repository.TransactionRepository;
import com.simplebank.repository.UserRepository;
import com.simplebank.repository.inmemory.InMemoryAccountRepository;
import com.simplebank.repository.inmemory.InMemoryTransactionRepository;
import com.simplebank.repository.inmemory.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests: no Spring, no mocks. The in-memory repositories make
 * the service easy to test directly.
 */
class AccountServiceTest {

    private UserRepository userRepository;
    private TransactionRepository transactionRepository;
    private AccountService accountService;
    private Long userId;
    private Long accountId;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        transactionRepository = new InMemoryTransactionRepository();
        accountService = new AccountService(new InMemoryAccountRepository(), userRepository, transactionRepository);

        userId = userRepository.save(new User("John Doe", "john@example.com")).getUserId();
        accountId = accountService.createAccount(userId, AccountType.SAVINGS).accountId();
    }

    // ----- Create / Read -----

    @Test
    void newAccountStartsWithZeroBalance() {
        AccountResponse account = accountService.getAccount(accountId);
        assertThat(account.balance()).isEqualByComparingTo("0.00");
        assertThat(account.userName()).isEqualTo("John Doe");
    }

    @Test
    void cannotCreateAccountForUnknownUser() {
        assertThatThrownBy(() -> accountService.createAccount(999L, AccountType.CHECKING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userCanHaveMultipleAccounts() {
        accountService.createAccount(userId, AccountType.CHECKING);

        List<AccountResponse> accounts = accountService.getAccountsForUser(userId);
        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(AccountResponse::accountType)
                .containsExactly(AccountType.SAVINGS, AccountType.CHECKING);
    }

    @Test
    void accountsForUnknownUserThrowsNotFound() {
        assertThatThrownBy(() -> accountService.getAccountsForUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllAccountsIncludesEveryUsersAccounts() {
        Long janeId = userRepository.save(new User("Jane Doe", "jane@example.com")).getUserId();
        accountService.createAccount(janeId, AccountType.CHECKING);

        assertThat(accountService.getAllAccounts())
                .extracting(AccountResponse::userName)
                .containsExactly("John Doe", "Jane Doe");
    }

    @Test
    void unknownAccountThrowsNotFound() {
        assertThatThrownBy(() -> accountService.getAccount(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.getTransactions(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Update -----

    @Test
    void updateAccountChangesTypeButNotBalance() {
        accountService.deposit(accountId, new BigDecimal("250"));

        AccountResponse updated = accountService.updateAccount(accountId, AccountType.CHECKING);

        assertThat(updated.accountType()).isEqualTo(AccountType.CHECKING);
        assertThat(updated.balance()).isEqualByComparingTo("250.00");
    }

    @Test
    void updateUnknownAccountThrowsNotFound() {
        assertThatThrownBy(() -> accountService.updateAccount(999L, AccountType.CHECKING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Deposit / Withdraw -----

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        AccountResponse account = accountService.deposit(accountId, new BigDecimal("500"));

        assertThat(account.balance()).isEqualByComparingTo("500.00");
        List<TransactionResponse> txns = accountService.getTransactions(accountId);
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(txns.get(0).amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void withdrawDecreasesBalanceAndRecordsTransaction() {
        accountService.deposit(accountId, new BigDecimal("500"));
        AccountResponse account = accountService.withdraw(accountId, new BigDecimal("200"));

        assertThat(account.balance()).isEqualByComparingTo("300.00");
        assertThat(accountService.getTransactions(accountId)).hasSize(2);
    }

    @Test
    void cannotWithdrawMoreThanBalance() {
        accountService.deposit(accountId, new BigDecimal("100"));

        assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);

        // A failed withdrawal must not change the balance or record a transaction
        assertThat(accountService.getAccount(accountId).balance()).isEqualByComparingTo("100.00");
        assertThat(accountService.getTransactions(accountId)).hasSize(1);
    }

    @Test
    void canWithdrawEntireBalance() {
        accountService.deposit(accountId, new BigDecimal("100"));
        AccountResponse account = accountService.withdraw(accountId, new BigDecimal("100"));
        assertThat(account.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void depositMustBePositive() {
        assertThatThrownBy(() -> accountService.deposit(accountId, BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("-50")))
                .isInstanceOf(InvalidAmountException.class);
        assertThat(accountService.getTransactions(accountId)).isEmpty();
    }

    @Test
    void withdrawMustBePositive() {
        assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("-10")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void amountCannotHaveMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("10.005")))
                .isInstanceOf(InvalidAmountException.class);
        // Trailing zeros are fine
        assertThat(accountService.deposit(accountId, new BigDecimal("10.500")).balance())
                .isEqualByComparingTo("10.50");
    }

    @Test
    void decimalArithmeticIsExact() {
        accountService.deposit(accountId, new BigDecimal("0.10"));
        AccountResponse account = accountService.deposit(accountId, new BigDecimal("0.20"));
        assertThat(account.balance()).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void transactionsAreReturnedNewestFirst() {
        accountService.deposit(accountId, new BigDecimal("100"));
        accountService.withdraw(accountId, new BigDecimal("40"));

        List<TransactionResponse> txns = accountService.getTransactions(accountId);
        assertThat(txns.get(0).type()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(txns.get(1).type()).isEqualTo(TransactionType.DEPOSIT);
    }

    // ----- Delete -----

    @Test
    void deleteEmptyAccountRemovesItAndItsTransactions() {
        accountService.deposit(accountId, new BigDecimal("50"));
        accountService.withdraw(accountId, new BigDecimal("50"));

        accountService.deleteAccount(accountId);

        assertThatThrownBy(() -> accountService.getAccount(accountId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(transactionRepository.findByAccountIdOrderByTxnIdDesc(accountId)).isEmpty();
    }

    @Test
    void cannotDeleteAccountWithMoneyInIt() {
        accountService.deposit(accountId, new BigDecimal("0.01"));

        assertThatThrownBy(() -> accountService.deleteAccount(accountId))
                .isInstanceOf(OperationNotAllowedException.class);
        assertThat(accountService.getAccount(accountId).balance()).isEqualByComparingTo("0.01");
    }

    @Test
    void deleteUnknownAccountThrowsNotFound() {
        assertThatThrownBy(() -> accountService.deleteAccount(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
