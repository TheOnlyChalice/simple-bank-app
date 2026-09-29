package com.simplebank.dto;

import com.simplebank.model.AccountType;
import jakarta.validation.constraints.NotNull;

/**
 * Body for PUT /api/accounts/{id}. Only the account type is editable:
 * there is deliberately no balance field, so a balance can't be set directly.
 */
public record UpdateAccountRequest(
        @NotNull(message = "accountType is required (SAVINGS or CHECKING)")
        AccountType accountType
) {
}
