package com.simplebank.mongo;

import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import com.simplebank.dto.AccountResponse;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.model.AccountType;
import com.simplebank.service.AccountService;
import com.simplebank.service.UserService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves two things against a real MongoDB:
 *  1. The validation rules and unique index reject bad data even when it is written
 *     directly to MongoDB, bypassing the app (the equivalent of SQL CHECK constraints).
 *  2. Transactions with retry keep balances correct when requests hit the same
 *     account at the same moment.
 */
class MongoRulesAndConcurrencyTest extends MongoTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    // ----- Rules enforced by MongoDB itself -----

    @Test
    void mongoRejectsNegativeBalance() {
        long accountId = newAccountWithBalance("0");

        assertThatThrownBy(() -> mongoTemplate.getCollection("accounts").updateOne(
                Filters.eq("_id", accountId),
                Updates.set("balance", new Decimal128(new BigDecimal("-1.00")))))
                .isInstanceOf(MongoWriteException.class)
                .hasMessageContaining("Document failed validation");
    }

    @Test
    void mongoRejectsInvalidAccountType() {
        long accountId = newAccountWithBalance("0");

        assertThatThrownBy(() -> mongoTemplate.getCollection("accounts").updateOne(
                Filters.eq("_id", accountId),
                Updates.set("accountType", "GOLD")))
                .isInstanceOf(MongoWriteException.class)
                .hasMessageContaining("Document failed validation");
    }

    @Test
    void mongoRejectsZeroAmountTransaction() {
        long accountId = newAccountWithBalance("0");

        assertThatThrownBy(() -> mongoTemplate.getCollection("transactions").insertOne(
                transactionDocument(accountId, "DEPOSIT", "0.00")))
                .isInstanceOf(MongoWriteException.class)
                .hasMessageContaining("Document failed validation");
    }

    @Test
    void mongoRequiresTransfersToNameTheOtherAccount() {
        long accountId = newAccountWithBalance("0");

        assertThatThrownBy(() -> mongoTemplate.getCollection("transactions").insertOne(
                transactionDocument(accountId, "TRANSFER_IN", "10.00")))
                .isInstanceOf(MongoWriteException.class)
                .hasMessageContaining("Document failed validation");
    }

    @Test
    void mongoRejectsDuplicateEmail() {
        userService.createUser("Jane", "jane@example.com", TestData.ADDRESS);

        assertThatThrownBy(() -> mongoTemplate.getCollection("users").insertOne(new Document()
                .append("_id", 999_999L)
                .append("name", "Other Jane")
                .append("email", "jane@example.com")
                .append("address", new Document("street", "1 Main St").append("city", "Baltimore")
                        .append("state", "MD").append("zip", "21201"))
                .append("createdAt", new Date())))
                .isInstanceOf(MongoWriteException.class)
                .hasMessageContaining("E11000"); // MongoDB's "duplicate key" error code
    }

    // ----- Concurrency: requests arriving at the same moment -----

    @Test
    void simultaneousWithdrawalsCannotOverdraw() throws Exception {
        long accountId = newAccountWithBalance("100");

        List<Object> outcomes = runAtTheSameTime(2,
                () -> accountService.withdraw(accountId, new BigDecimal("80")));

        assertThat(outcomes).filteredOn(o -> o instanceof AccountResponse).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof InsufficientFundsException).hasSize(1);
        assertThat(accountService.getAccount(accountId).balance()).isEqualByComparingTo("20.00");
    }

    @Test
    void simultaneousDepositsAreAllCounted() throws Exception {
        long accountId = newAccountWithBalance("0");

        List<Object> outcomes = runAtTheSameTime(10,
                () -> accountService.deposit(accountId, new BigDecimal("10")));

        assertThat(outcomes).allMatch(o -> o instanceof AccountResponse);
        assertThat(accountService.getAccount(accountId).balance()).isEqualByComparingTo("100.00");
        assertThat(accountService.getTransactions(accountId, 0, 100).totalElements()).isEqualTo(10);
    }

    @Test
    void simultaneousTransfersInBothDirectionsAllSucceed() throws Exception {
        long a = newAccountWithBalance("1000");
        long b = newAccountWithBalance("1000");

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> accountService.transfer(a, b, new BigDecimal("1")));
            tasks.add(() -> accountService.transfer(b, a, new BigDecimal("1")));
        }
        List<Object> outcomes = runAtTheSameTime(tasks);

        assertThat(outcomes).allMatch(o -> !(o instanceof Throwable));
        BigDecimal total = accountService.getAccount(a).balance().add(accountService.getAccount(b).balance());
        assertThat(total).isEqualByComparingTo("2000.00"); // no money created or lost
    }

    // ----- Helpers -----

    private long newAccountWithBalance(String balance) {
        long userId = userService.createUser("Test User", UUID.randomUUID() + "@example.com", TestData.ADDRESS).userId();
        long accountId = accountService.createAccount(userId, AccountType.SAVINGS).accountId();
        if (new BigDecimal(balance).signum() > 0) {
            accountService.deposit(accountId, new BigDecimal(balance));
        }
        return accountId;
    }

    private Document transactionDocument(long accountId, String type, String amount) {
        return new Document()
                .append("_id", 999_999L)
                .append("accountId", accountId)
                .append("txnType", type)
                .append("amount", new Decimal128(new BigDecimal(amount)))
                .append("createdAt", new Date());
    }

    /** Runs the same task on several threads, all released at the same instant. */
    private List<Object> runAtTheSameTime(int threads, Callable<Object> task) throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(task);
        }
        return runAtTheSameTime(tasks);
    }

    /**
     * Starts every task on its own thread, holds them at a starting line, then releases
     * them together. Returns each task's result, or the exception it threw.
     */
    private List<Object> runAtTheSameTime(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startingLine = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    startingLine.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            startingLine.countDown(); // go!

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
