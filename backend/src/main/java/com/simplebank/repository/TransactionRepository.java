package com.simplebank.repository;

import com.simplebank.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** One page of an account's history. The page number, size, and sort order come from the Pageable. */
    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    /** Newest first. IDs only ever increase, so ordering by ID is ordering by time. */
    List<Transaction> findByAccountIdOrderByTxnIdDesc(Long accountId);

    void deleteByAccountId(Long accountId);
}
