package com.simplebank.controller;

import com.simplebank.dto.LoginRequest;
import com.simplebank.dto.RegisterRequest;
import com.simplebank.dto.TokenResponse;
import com.simplebank.dto.UserResponse;
import com.simplebank.security.AccessGuard;
import com.simplebank.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Register, log in, and "who am I?". Register and login are open to everyone. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessGuard accessGuard;

    public AuthController(AuthService authService, AccessGuard accessGuard) {
        this.authService = authService;
        this.accessGuard = accessGuard;
    }

    /** Creates a CUSTOMER and returns a login token right away. */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authService.register(
                request.name(), request.email(), request.address().toAddress(), request.password());
        return ResponseEntity.created(URI.create("/api/users/" + response.user().userId())).body(response);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    /** The logged-in user's own profile. */
    @GetMapping("/me")
    public UserResponse me() {
        return authService.me(accessGuard.currentUser().userId());
    }
}
