package com.simplebank.exception;

import java.math.BigDecimal;

/** Thrown when a withdrawal is larger than the balance. Becomes a 400. */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds: balance is " + balance + ", requested " + requested);
    }
}
