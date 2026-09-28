package com.simplebank.dto;

import com.simplebank.model.Transaction;
import com.simplebank.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Matches the "Transaction Response" in section 8, with txnId added for the UI table. */
public record TransactionResponse(
        Long txnId,
        TransactionType type,
        BigDecimal amount,
        LocalDateTime date
) {
    public static TransactionResponse from(Transaction txn) {
        return new TransactionResponse(txn.getTxnId(), txn.getTxnType(), txn.getAmount(), txn.getCreatedAt());
    }
}
