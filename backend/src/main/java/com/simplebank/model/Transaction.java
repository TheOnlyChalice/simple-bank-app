package com.simplebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the TRANSACTIONS table.
 * A new Transaction is recorded for every deposit and withdrawal.
 */
public class Transaction {

    private Long txnId;
    private Long accountId;
    private TransactionType txnType;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    public Transaction() {
    }

    public Transaction(Long accountId, TransactionType txnType, BigDecimal amount) {
        this.accountId = accountId;
        this.txnType = txnType;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getTxnId() { return txnId; }
    public void setTxnId(Long txnId) { this.txnId = txnId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public TransactionType getTxnType() { return txnType; }
    public void setTxnType(TransactionType txnType) { this.txnType = txnType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
