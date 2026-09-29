package com.simplebank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A document in the "transactions" collection: one per deposit, withdrawal,
 * or side of a transfer. The index makes "history for account X, newest first" fast.
 */
@Document(collection = "transactions")
@CompoundIndex(name = "account_history", def = "{'accountId': 1, '_id': -1}")
public class Transaction {

    @Id
    private Long txnId;

    private Long accountId;

    /** Stored as its name, e.g. "DEPOSIT". */
    private TransactionType txnType;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    /** For transfers: the other account. Not stored for deposits and withdrawals. */
    private Long relatedAccountId;

    private LocalDateTime createdAt;

    public Transaction() {
    }

    /** Deposit or withdrawal. */
    public Transaction(Long accountId, TransactionType txnType, BigDecimal amount) {
        this(accountId, txnType, amount, null);
    }

    /** One side of a transfer; relatedAccountId is the other account. */
    public Transaction(Long accountId, TransactionType txnType, BigDecimal amount, Long relatedAccountId) {
        this.accountId = accountId;
        this.txnType = txnType;
        this.amount = amount;
        this.relatedAccountId = relatedAccountId;
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

    public Long getRelatedAccountId() { return relatedAccountId; }
    public void setRelatedAccountId(Long relatedAccountId) { this.relatedAccountId = relatedAccountId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
