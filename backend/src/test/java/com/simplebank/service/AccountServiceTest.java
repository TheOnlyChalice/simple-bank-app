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
import com.simplebank.model.AccountType;
import com.simplebank.model.TransactionType;
import com.simplebank.model.User;
import com.simplebank.repository.TransactionRepository;
import com.simplebank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the H2 test database (src/test/resources/application.properties).
 * @Transactional rolls back every test's changes, so each test starts with an empty database.
 */
@SpringBootTest
@Transactional
class AccountServiceTest {

    private static final Long UNKNOWN_ID = 999_999L;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Long userId;
    private Long accountId;

    @BeforeEach
    void setUp() {
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
        assertThatThrownBy(() -> accountService.createAccount(UNKNOWN_ID, AccountType.CHECKING))
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
        assertThatThrownBy(() -> accountService.getAccountsForUser(UNKNOWN_ID))
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
        assertThatThrownBy(() -> accountService.getAccount(UNKNOWN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> history(UNKNOWN_ID))
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
        assertThatThrownBy(() -> accountService.updateAccount(UNKNOWN_ID, AccountType.CHECKING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Deposit / Withdraw -----

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        AccountResponse account = accountService.deposit(accountId, new BigDecimal("500"));

        assertThat(account.balance()).isEqualByComparingTo("500.00");
        List<TransactionResponse> txns = history(accountId);
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(txns.get(0).amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void withdrawDecreasesBalanceAndRecordsTransaction() {
        accountService.deposit(accountId, new BigDecimal("500"));
        AccountResponse account = accountService.withdraw(accountId, new BigDecimal("200"));

        assertThat(account.balance()).isEqualByComparingTo("300.00");
        assertThat(history(accountId)).hasSize(2);
    }

    @Test
    void cannotWithdrawMoreThanBalance() {
        accountService.deposit(accountId, new BigDecimal("100"));

        assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);

        // A failed withdrawal must not change the balance or record a transaction
        assertThat(accountService.getAccount(accountId).balance()).isEqualByComparingTo("100.00");
        assertThat(history(accountId)).hasSize(1);
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
        assertThat(history(accountId)).isEmpty();
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

        List<TransactionResponse> txns = history(accountId);
        assertThat(txns.get(0).type()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(txns.get(1).type()).isEqualTo(TransactionType.DEPOSIT);
    }

    // ----- Transfer -----

    @Test
    void transferMovesMoneyAndRecordsBothSides() {
        Long otherId = accountService.createAccount(userId, AccountType.CHECKING).accountId();
        accountService.deposit(accountId, new BigDecimal("500"));

        TransferResponse result = accountService.transfer(accountId, otherId, new BigDecimal("200"));

        assertThat(result.fromAccount().balance()).isEqualByComparingTo("300.00");
        assertThat(result.toAccount().balance()).isEqualByComparingTo("200.00");
        assertThat(result.amount()).isEqualByComparingTo("200.00");

        TransactionResponse out = history(accountId).get(0);
        assertThat(out.type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(out.relatedAccountId()).isEqualTo(otherId);

        TransactionResponse in = history(otherId).get(0);
        assertThat(in.type()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(in.relatedAccountId()).isEqualTo(accountId);
    }

    @Test
    void transferWorksBetweenDifferentUsers() {
        Long janeId = userRepository.save(new User("Jane Doe", "jane@example.com")).getUserId();
        Long janeAccountId = accountService.createAccount(janeId, AccountType.CHECKING).accountId();
        accountService.deposit(accountId, new BigDecimal("100"));

        TransferResponse result = accountService.transfer(accountId, janeAccountId, new BigDecimal("40"));

        assertThat(result.toAccount().userName()).isEqualTo("Jane Doe");
        assertThat(result.toAccount().balance()).isEqualByComparingTo("40.00");
    }

    @Test
    void cannotTransferMoreThanBalance() {
        Long otherId = accountService.createAccount(userId, AccountType.CHECKING).accountId();
        accountService.deposit(accountId, new BigDecimal("100"));

        assertThatThrownBy(() -> accountService.transfer(accountId, otherId, new BigDecimal("150")))
                .isInstanceOf(InsufficientFundsException.class);

        // Nothing changed on either side
        assertThat(accountService.getAccount(accountId).balance()).isEqualByComparingTo("100.00");
        assertThat(accountService.getAccount(otherId).balance()).isEqualByComparingTo("0.00");
        assertThat(history(otherId)).isEmpty();
    }

    @Test
    void cannotTransferToSameAccount() {
        accountService.deposit(accountId, new BigDecimal("100"));
        assertThatThrownBy(() -> accountService.transfer(accountId, accountId, new BigDecimal("10")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void transferAmountMustBePositive() {
        Long otherId = accountService.createAccount(userId, AccountType.CHECKING).accountId();
        assertThatThrownBy(() -> accountService.transfer(accountId, otherId, new BigDecimal("-5")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void transferToUnknownAccountThrowsNotFound() {
        accountService.deposit(accountId, new BigDecimal("100"));

        assertThatThrownBy(() -> accountService.transfer(accountId, UNKNOWN_ID, new BigDecimal("10")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(accountService.getAccount(accountId).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void historyKeepsTransfersAfterOtherAccountIsDeleted() {
        Long otherId = accountService.createAccount(userId, AccountType.CHECKING).accountId();
        accountService.deposit(accountId, new BigDecimal("100"));
        accountService.transfer(accountId, otherId, new BigDecimal("100"));
        accountService.withdraw(otherId, new BigDecimal("100"));

        accountService.deleteAccount(otherId);

        TransactionResponse out = history(accountId).get(0);
        assertThat(out.type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(out.relatedAccountId()).isEqualTo(otherId);
    }

    // ----- Pagination -----

    /** The first 100 history entries, newest first. Plenty for these tests. */
    private List<TransactionResponse> history(Long id) {
        return accountService.getTransactions(id, 0, 100).content();
    }

    @Test
    void transactionsArePagedNewestFirst() {
        for (int i = 1; i <= 5; i++) {
            accountService.deposit(accountId, new BigDecimal(i));
        }

        PageResponse<TransactionResponse> firstPage = accountService.getTransactions(accountId, 0, 2);
        assertThat(firstPage.content()).extracting(TransactionResponse::amount)
                .containsExactly(new BigDecimal("5.00"), new BigDecimal("4.00"));
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.first()).isTrue();
        assertThat(firstPage.last()).isFalse();

        PageResponse<TransactionResponse> lastPage = accountService.getTransactions(accountId, 2, 2);
        assertThat(lastPage.content()).extracting(TransactionResponse::amount)
                .containsExactly(new BigDecimal("1.00"));
        assertThat(lastPage.last()).isTrue();
    }

    @Test
    void pageBeyondTheEndIsEmpty() {
        accountService.deposit(accountId, new BigDecimal("10"));

        PageResponse<TransactionResponse> page = accountService.getTransactions(accountId, 5, 10);
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void invalidPageParametersAreRejected() {
        assertThatThrownBy(() -> accountService.getTransactions(accountId, -1, 10))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> accountService.getTransactions(accountId, 0, 0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> accountService.getTransactions(accountId, 0, 101))
                .isInstanceOf(InvalidRequestException.class);
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
        assertThatThrownBy(() -> accountService.deleteAccount(UNKNOWN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
