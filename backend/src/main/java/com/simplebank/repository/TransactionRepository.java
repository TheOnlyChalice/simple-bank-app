package com.simplebank.repository;

import com.simplebank.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Newest first. IDs only ever increase, so ordering by ID is ordering by time. */
    List<Transaction> findByAccountIdOrderByTxnIdDesc(Long accountId);

    void deleteByAccountId(Long accountId);
}
