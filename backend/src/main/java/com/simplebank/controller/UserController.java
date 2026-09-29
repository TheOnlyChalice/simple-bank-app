package com.simplebank.controller;

import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.CreateUserRequest;
import com.simplebank.dto.PageResponse;
import com.simplebank.dto.UpdateUserRequest;
import com.simplebank.dto.UserResponse;
import com.simplebank.repository.BalanceMode;
import com.simplebank.repository.UserFilter;
import com.simplebank.service.AccountService;
import com.simplebank.service.UserService;
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
import java.util.List;

/**
 * CRUD and search for users. The Create Account page collects a name, email, and
 * address, so users are created before an account can be opened.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    public UserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(
                request.name(), request.email(), request.address().toAddress());
        return ResponseEntity.created(URI.create("/api/users/" + user.userId())).body(user);
    }

    /**
     * Paginated, oldest first, with optional filters, e.g.
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
        return userService.getUser(id);
    }

    /** One user -> many accounts. */
    @GetMapping("/{id}/accounts")
    public List<AccountResponse> getUserAccounts(@PathVariable Long id) {
        return accountService.getAccountsForUser(id);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request.name(), request.email(), request.address().toAddress());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
