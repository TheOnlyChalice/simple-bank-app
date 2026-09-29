package com.simplebank.service;

import com.simplebank.exception.InvalidRequestException;

import java.math.BigDecimal;

/** Checks search filters before they reach the database. */
final class SearchRules {

    private SearchRules() {
    }

    static void checkBalanceRange(BigDecimal minBalance, BigDecimal maxBalance) {
        if ((minBalance != null && minBalance.signum() < 0) || (maxBalance != null && maxBalance.signum() < 0)) {
            throw new InvalidRequestException("minBalance and maxBalance cannot be negative");
        }
        if (minBalance != null && maxBalance != null && minBalance.compareTo(maxBalance) > 0) {
            throw new InvalidRequestException("minBalance cannot be greater than maxBalance");
        }
    }
}
