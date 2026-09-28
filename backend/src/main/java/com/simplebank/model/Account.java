package com.simplebank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the ACCOUNTS table.
 * Money is always BigDecimal, never double, so amounts are exact.
 */
public class Account {

    private Long accountId;
    private Long userId;
    private BigDecimal balance;
    private AccountType accountType;
    private LocalDateTime createdAt;

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
