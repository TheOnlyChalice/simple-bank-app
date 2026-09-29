package com.simplebank.repository;

import com.simplebank.model.Transaction;

import java.util.List;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    /** Newest first. IDs only ever increase, so ordering by ID is ordering by time. */
    List<Transaction> findByAccountIdOrderByTxnIdDesc(Long accountId);

    void deleteByAccountId(Long accountId);
}
