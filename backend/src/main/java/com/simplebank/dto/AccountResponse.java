package com.simplebank.dto;

import com.simplebank.model.Account;
import com.simplebank.model.AccountType;
import com.simplebank.model.Role;
import com.simplebank.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Matches the "Account Response" in section 8, plus a few useful extra fields.
 * frozenBy is CUSTOMER or ADMIN (the bank), and null when the account isn't frozen.
 */
public record AccountResponse(
        Long accountId,
        Long userId,
        String userName,
        AccountType accountType,
        BigDecimal balance,
        LocalDateTime createdAt,
        boolean frozen,
        Role frozenBy,
        LocalDateTime frozenAt
) {
    public static AccountResponse from(Account account, User user) {
        return new AccountResponse(
                account.getAccountId(),
                user.getUserId(),
                user.getName(),
                account.getAccountType(),
                account.getBalance(),
                account.getCreatedAt(),
                account.isFrozen(),
                account.getFrozenBy(),
                account.getFrozenAt());
    }
}
