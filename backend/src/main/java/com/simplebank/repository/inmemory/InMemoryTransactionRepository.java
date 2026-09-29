package com.simplebank.repository.inmemory;

import com.simplebank.model.Transaction;
import com.simplebank.repository.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<Long, Transaction> transactions = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Transaction save(Transaction transaction) {
        if (transaction.getTxnId() == null) {
            transaction.setTxnId(nextId.getAndIncrement());
        }
        transactions.put(transaction.getTxnId(), transaction);
        return transaction;
    }

    @Override
    public List<Transaction> findByAccountIdOrderByTxnIdDesc(Long accountId) {
        return transactions.values().stream()
                .filter(txn -> txn.getAccountId().equals(accountId))
                .sorted(Comparator.comparing(Transaction::getTxnId).reversed())
                .toList();
    }

    @Override
    public void deleteByAccountId(Long accountId) {
        transactions.values().removeIf(txn -> txn.getAccountId().equals(accountId));
    }
}
