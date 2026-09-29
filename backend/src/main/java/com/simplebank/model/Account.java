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
 * JPA entity mapped to the ACCOUNTS table (see database/schema.sql).
 * Money is always BigDecimal, never double, so amounts are exact.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    /** Foreign key to users.user_id: one user can have many accounts. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)          // store "SAVINGS"/"CHECKING", not 0/1
    @JdbcTypeCode(SqlTypes.VARCHAR)       // as a VARCHAR column, matching schema.sql
    @Column(name = "account_type", nullable = false, length = 50)
    private AccountType accountType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Required by JPA, which creates objects when loading rows. */
    public Account() {
    }

    public Account(Long userId, AccountType accountType) {
        this.userId = userId;
        this.accountType = accountType;
        this.balance = BigDecimal.ZERO.setScale(2); // 0.00, matching DECIMAL(10,2)
        this.createdAt = LocalDateTime.now();
    }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
