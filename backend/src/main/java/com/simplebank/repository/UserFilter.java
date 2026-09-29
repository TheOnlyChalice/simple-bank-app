package com.simplebank.repository;

import java.math.BigDecimal;

/**
 * Optional filters for searching users. Any field left null is ignored.
 * Blank values count as "not set", and the state is compared uppercase.
 */
public record UserFilter(
        String state,
        String city,
        String zip,
        BigDecimal minBalance,
        BigDecimal maxBalance,
        BalanceMode balanceMode
) {
    /** No filters: every user. */
    public static final UserFilter NONE = new UserFilter(null, null, null, null, null, null);

    public UserFilter {
        state = blankToNull(state);
        if (state != null) {
            state = state.toUpperCase();
        }
        city = blankToNull(city);
        zip = blankToNull(zip);
        if (balanceMode == null) {
            balanceMode = BalanceMode.TOTAL;
        }
    }

    public boolean hasBalanceRange() {
        return minBalance != null || maxBalance != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
