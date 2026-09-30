package com.simplebank.controller;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.PageResponse;
import com.simplebank.dto.UpdateUserRequest;
import com.simplebank.dto.UserResponse;
import com.simplebank.repository.BalanceMode;
import com.simplebank.repository.UserFilter;
import com.simplebank.security.AccessGuard;
import com.simplebank.service.AccountService;
import com.simplebank.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Users. New customers register through POST /api/auth/register.
 * Listing and searching all users is ADMIN only (see SecurityConfig); for everything
 * else, customers can only reach their own profile.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AccountService accountService;
    private final AccessGuard accessGuard;

    public UserController(UserService userService, AccountService accountService, AccessGuard accessGuard) {
        this.userService = userService;
        this.accountService = accountService;
        this.accessGuard = accessGuard;
    }

    /**
     * ADMIN only. Paginated, oldest first, with optional filters, e.g.
     * /api/users?state=MD&city=Baltimore&minBalance=100&balanceMode=ANY_ACCOUNT
     */
    @GetMapping
    public PageResponse<UserResponse> getAllUsers(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) BigDecimal maxBalance,
            @RequestParam(defaultValue = "TOTAL") BalanceMode balanceMode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserFilter filter = new UserFilter(state, city, zip, minBalance, maxBalance, balanceMode);
        return userService.getAllUsers(filter, page, size);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        accessGuard.requireSelfOrAdmin(id);
        return userService.getUser(id);
    }

    /** One user -> many accounts. */
    @GetMapping("/{id}/accounts")
    public List<AccountResponse> getUserAccounts(@PathVariable Long id) {
        accessGuard.requireSelfOrAdmin(id);
        return accountService.getAccountsForUser(id);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        accessGuard.requireSelfOrAdmin(id);
        return userService.updateUser(id, request.name(), request.email(), request.address().toAddress());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        accessGuard.requireSelfOrAdmin(id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
