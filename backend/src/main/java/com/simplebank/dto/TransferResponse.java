package com.simplebank.dto;

import java.math.BigDecimal;

/** Both accounts after the transfer, plus the amount moved. */
public record TransferResponse(
        AccountResponse fromAccount,
        AccountResponse toAccount,
        BigDecimal amount
) {
}
