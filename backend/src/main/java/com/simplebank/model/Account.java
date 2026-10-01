package com.simplebank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A document in the "accounts" collection. One user can have many accounts.
 * Money is BigDecimal in Java and Decimal128 in MongoDB, so amounts are exact.
 */
@Document(collection = "accounts")
public class Account {

    @Id
    private Long accountId;

    /** The owner. MongoDB has no foreign keys, so the services check that the user exists. */
    @Indexed
    private Long userId;

    /** Indexed so searches by balance range don't have to scan every account. */
    @Indexed
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal balance;

    /** Stored as its name, e.g. "SAVINGS". */
    private AccountType accountType;

    private LocalDateTime createdAt;

    /** A frozen account can't receive or send money until it is unfrozen. */
    private boolean frozen;

    /** Who froze it: a CUSTOMER can undo their own freeze; a freeze by the bank (ADMIN) needs staff. */
    private Role frozenBy;

    private LocalDateTime frozenAt;

    public Account() {
    }

    public Account(Long userId, AccountType accountType) {
        this.userId = userId;
        this.accountType = accountType;
        this.balance = BigDecimal.ZERO.setScale(2); // 0.00
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

    public boolean isFrozen() { return frozen; }
    public Role getFrozenBy() { return frozenBy; }
    public LocalDateTime getFrozenAt() { return frozenAt; }

    public void freeze(Role by) {
        this.frozen = true;
        this.frozenBy = by;
        this.frozenAt = LocalDateTime.now();
    }

    public void unfreeze() {
        this.frozen = false;
        this.frozenBy = null;
        this.frozenAt = null;
    }
}
