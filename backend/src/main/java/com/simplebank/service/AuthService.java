package com.simplebank.service;

import com.simplebank.dto.TokenResponse;
import com.simplebank.dto.UserResponse;
import com.simplebank.exception.InvalidCredentialsException;
import com.simplebank.model.Address;
import com.simplebank.model.AuditAction;
import com.simplebank.model.Role;
import com.simplebank.model.User;
import com.simplebank.repository.UserRepository;
import com.simplebank.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Registration and login. Passwords are only ever stored as BCrypt hashes, and every
 * login attempt, successful or not, is recorded in the audit log.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditService auditService;

    /**
     * Checked when the email doesn't exist, so a login for an unknown email takes as long as
     * one with a wrong password. Otherwise the response time would reveal which emails exist.
     */
    private final String dummyHash;

    public AuthService(UserService userService, UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService, AuditService auditService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");
    }

    /** Self-registration: always creates a CUSTOMER, and logs them straight in. */
    public TokenResponse register(String name, String email, Address address, String password) {
        UserResponse created = userService.registerUser(
                name, email, address, passwordEncoder.encode(password), Role.CUSTOMER);
        return tokenFor(userRepository.findById(created.userId()).orElseThrow());
    }

    /** The same "Invalid email or password" error whether the email or the password is wrong. */
    public TokenResponse login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        AuditDetails audit = AuditDetails.none().withDetails("email " + normalizedEmail);
        return auditService.recordFailures(AuditAction.LOGIN, audit, () -> {
            Optional<User> user = userRepository.findByEmail(normalizedEmail);
            String hash = user.map(User::getPasswordHash).orElse(null);
            boolean passwordMatches = passwordEncoder.matches(password, hash != null ? hash : dummyHash);

            if (user.isEmpty() || hash == null || !passwordMatches) {
                throw new InvalidCredentialsException();
            }
            auditService.recordSuccess(AuditAction.LOGIN, audit.withUserId(user.get().getUserId()), List.of());
            return tokenFor(user.get());
        });
    }

    public UserResponse me(Long userId) {
        return userService.getUser(userId);
    }

    private TokenResponse tokenFor(User user) {
        TokenService.IssuedToken token = tokenService.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(user));
    }
}
