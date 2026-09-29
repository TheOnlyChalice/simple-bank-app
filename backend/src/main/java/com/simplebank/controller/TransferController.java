package com.simplebank.controller;

import com.simplebank.dto.TransferRequest;
import com.simplebank.dto.TransferResponse;
import com.simplebank.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transfers involve two accounts, so they have their own URL
 * instead of living under a single account.
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final AccountService accountService;

    public TransferController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** Returns 200 with both updated accounts. */
    @PostMapping
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return accountService.transfer(request.fromAccountId(), request.toAccountId(), request.amount());
    }
}
