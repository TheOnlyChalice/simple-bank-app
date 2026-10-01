package com.simplebank.controller;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.AmountRequest;
import com.simplebank.dto.CreateAccountRequest;
import com.simplebank.dto.PageResponse;
import com.simplebank.dto.TransactionResponse;
import com.simplebank.dto.UpdateAccountRequest;
import com.simplebank.model.AccountType;
import com.simplebank.repository.AccountFilter;
import com.simplebank.security.AccessGuard;
import com.simplebank.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Accounts, premium accounts, deposit, withdraw, and transaction history (section 5.4).
 * Listing all accounts and premium accounts is ADMIN only (see SecurityConfig); for
 * everything else, customers can only reach their own accounts (AccessGuard).
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccessGuard accessGuard;

    public AccountController(AccountService accountService, AccessGuard accessGuard) {
        this.accountService = accountService;
        this.accessGuard = accessGuard;
    }

    // ----- CRUD -----

    /** Customers can only open accounts for themselves. */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        accessGuard.requireSelfOrAdmin(request.userId());
        AccountResponse account = accountService.createAccount(request.userId(), request.accountType());
        return ResponseEntity.created(URI.create("/api/accounts/" + account.accountId())).body(account);
    }

    /**
     * ADMIN only. Paginated, oldest first, with optional filters, e.g.
     * /api/accounts?minBalance=100&maxBalance=500&accountType=SAVINGS
     */
    @GetMapping
    public PageResponse<AccountResponse> getAllAccounts(
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) BigDecimal maxBalance,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AccountFilter filter = new AccountFilter(minBalance, maxBalance, accountType);
        return accountService.getAllAccounts(filter, page, size);
    }

    /** ADMIN only. Balance at or above the threshold, richest first, e.g. /api/accounts/premium?threshold=1000 */
    @GetMapping("/premium")
    public PageResponse<AccountResponse> getPremiumAccounts(
            @RequestParam BigDecimal threshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accountService.getPremiumAccounts(threshold, page, size);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable Long id) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        return accountService.getAccount(id);
    }

    @PutMapping("/{id}")
    public AccountResponse updateAccount(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        return accountService.updateAccount(id, request.accountType());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    // ----- Freeze -----

    /**
     * Freezes the account: no deposits, withdrawals, or transfers in or out until it's unfrozen.
     * Customers can freeze their own accounts; staff can freeze any account.
     */
    @PostMapping("/{id}/freeze")
    public AccountResponse freeze(@PathVariable Long id) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        return accountService.freezeAccount(id, accessGuard.currentUser().role());
    }

    /** A customer can lift their own freeze; a freeze made by the bank can only be lifted by staff. */
    @PostMapping("/{id}/unfreeze")
    public AccountResponse unfreeze(@PathVariable Long id) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        return accountService.unfreezeAccount(id, accessGuard.currentUser().role());
    }

    // ----- Money -----

    @PostMapping("/{id}/deposit")
    public AccountResponse deposit(@PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        return accountService.deposit(id, request.amount());
    }

    @PostMapping("/{id}/withdraw")
    public AccountResponse withdraw(@PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        return accountService.withdraw(id, request.amount());
    }

    /** Paginated history, newest first, e.g. /api/accounts/1/transactions?page=0&size=10 */
    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        accessGuard.requireAccountOwnerOrAdmin(id);
        return accountService.getTransactions(id, page, size);
    }
}
