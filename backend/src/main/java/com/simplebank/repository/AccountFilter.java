package com.simplebank.repository;

import com.simplebank.model.AccountType;

import java.math.BigDecimal;

/** Optional filters for searching accounts. Any field left null is ignored. */
public record AccountFilter(
        BigDecimal minBalance,
        BigDecimal maxBalance,
        AccountType accountType
) {
    /** No filters: every account. */
    public static final AccountFilter NONE = new AccountFilter(null, null, null);
}
