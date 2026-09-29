package com.simplebank.service;

import com.mongodb.MongoException;
import com.simplebank.exception.ConcurrentUpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Runs work in a MongoDB transaction: all of its changes are saved, or none are.
 *
 * If two transactions change the same document at the same time, MongoDB aborts one
 * of them with a "TransientTransactionError" (a write conflict) instead of letting it
 * overwrite the other's change. That transaction is then retried from the beginning,
 * so it re-reads the latest data and re-checks the business rules.
 *
 * Retries continue for up to RETRY_BUDGET, waiting a little longer each time
 * (exponential backoff with random jitter) so competing requests spread out.
 */
@Component
public class MongoTransactions {

    private static final Logger log = LoggerFactory.getLogger(MongoTransactions.class);

    private static final Duration RETRY_BUDGET = Duration.ofSeconds(20);
    private static final long BASE_DELAY_MS = 50;
    private static final long MAX_DELAY_MS = 1_000;

    private final TransactionTemplate transactionTemplate;

    public MongoTransactions(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T run(Supplier<T> work) {
        long deadline = System.nanoTime() + RETRY_BUDGET.toNanos();
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (RuntimeException e) {
                if (!isWriteConflict(e)) {
                    throw e; // a business rule failed, or a real error: never retried
                }
                if (System.nanoTime() >= deadline) {
                    throw new ConcurrentUpdateException(e);
                }
                log.info("Write conflict on attempt {}, retrying", attempt);
                pauseBeforeRetry(attempt);
            }
        }
    }

    /** MongoDB labels errors that are safe to retry as a whole transaction. */
    private static boolean isWriteConflict(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exponential backoff with jitter: the longest possible wait doubles each attempt
     * (50 ms, 100 ms, 200 ms ... up to 1 s), and the actual wait is a random point in
     * the upper half of that range, so retrying requests don't collide again.
     */
    private static void pauseBeforeRetry(int attempt) {
        long ceiling = Math.min(MAX_DELAY_MS, BASE_DELAY_MS << Math.min(attempt - 1, 10));
        long delay = ThreadLocalRandom.current().nextLong(ceiling / 2, ceiling + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying a transaction", e);
        }
    }
}
