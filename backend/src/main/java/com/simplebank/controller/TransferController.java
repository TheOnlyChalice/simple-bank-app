package com.simplebank.controller;

import com.simplebank.dto.TransferRequest;
import com.simplebank.dto.TransferResponse;
import com.simplebank.security.AccessGuard;
import com.simplebank.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transfers involve two accounts, so they have their own URL instead of living under a
 * single account. Money can only be sent FROM your own account, but TO any account.
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final AccountService accountService;
    private final AccessGuard accessGuard;

    public TransferController(AccountService accountService, AccessGuard accessGuard) {
        this.accountService = accountService;
        this.accessGuard = accessGuard;
    }

    /** Returns 200 with both updated accounts. */
    @PostMapping
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        accessGuard.requireAccountOwnerOrAdmin(request.fromAccountId());
        return accountService.transfer(request.fromAccountId(), request.toAccountId(), request.amount());
    }
}
