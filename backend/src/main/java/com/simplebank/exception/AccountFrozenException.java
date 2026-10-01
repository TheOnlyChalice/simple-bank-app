package com.simplebank.exception;

/**
 * Money can't move into or out of a frozen account. A kind of OperationNotAllowedException,
 * so it becomes a 409 Conflict and is audited as REJECTED, like the other business rules.
 */
public class AccountFrozenException extends OperationNotAllowedException {

    public AccountFrozenException(Long accountId) {
        super("Account " + accountId + " is frozen. No deposits, withdrawals, or transfers are allowed until it is unfrozen.");
    }

    public AccountFrozenException(String message) {
        super(message);
    }
}
