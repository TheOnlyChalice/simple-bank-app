package com.simplebank.service;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.TransactionResponse;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.AccountType;
import com.simplebank.model.TransactionType;
import com.simplebank.model.User;
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

    private AccountService accountService;
    private Long accountId;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = new InMemoryUserRepository();
        accountService = new AccountService(
                new InMemoryAccountRepository(), userRepository, new InMemoryTransactionRepository());

        User user = userRepository.save(new User("John Doe", "john@example.com"));
        accountId = accountService.createAccount(user.getUserId(), AccountType.SAVINGS).accountId();
    }

    @Test
    void newAccountStartsWithZeroBalance() {
        AccountResponse account = accountService.getAccount(accountId);
        assertThat(account.balance()).isEqualByComparingTo("0.00");
        assertThat(account.userName()).isEqualTo("John Doe");
    }

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

    @Test
    void unknownAccountThrowsNotFound() {
        assertThatThrownBy(() -> accountService.getAccount(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.getTransactions(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cannotCreateAccountForUnknownUser() {
        assertThatThrownBy(() -> accountService.createAccount(999L, AccountType.CHECKING))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
