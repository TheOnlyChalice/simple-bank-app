package com.simplebank.repository;

/** How a user's balance is measured when filtering users by minBalance / maxBalance. */
public enum BalanceMode {
    /** The sum of all the user's accounts. A user with no accounts has a total of 0. */
    TOTAL,
    /** At least one of the user's accounts is in the range. */
    ANY_ACCOUNT
}
