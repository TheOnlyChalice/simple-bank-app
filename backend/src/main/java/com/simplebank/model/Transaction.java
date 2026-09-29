package com.simplebank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity mapped to the TRANSACTIONS table (see database/schema.sql).
 * One row per deposit, withdrawal, or side of a transfer.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "txn_id")
    private Long txnId;

    /** Foreign key to accounts.account_id. */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "txn_type", nullable = false, length = 20)
    private TransactionType txnType;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** For transfers: the other account. Null for deposits and withdrawals. */
    @Column(name = "related_account_id")
    private Long relatedAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Required by JPA, which creates objects when loading rows. */
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
