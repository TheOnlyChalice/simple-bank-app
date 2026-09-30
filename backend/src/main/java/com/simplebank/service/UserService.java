package com.simplebank.service;

import com.simplebank.dto.PageResponse;
import com.simplebank.dto.UserResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.Address;
import com.simplebank.model.AuditAction;
import com.simplebank.model.Role;
import com.simplebank.model.User;
import com.simplebank.repository.AccountRepository;
import com.simplebank.repository.UserFilter;
import com.simplebank.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * User operations. Each change runs in a MongoDB transaction together with its audit
 * event, so a change is never saved without its record in the audit log.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final MongoTransactions transactions;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       MongoTransactions transactions,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactions = transactions;
        this.auditService = auditService;
    }

    /** One page of all users, oldest first. Pages are numbered from 0. */
    public PageResponse<UserResponse> getAllUsers(int page, int size) {
        return getAllUsers(UserFilter.NONE, page, size);
    }

    /** One page of users matching the filter (address and/or balance), oldest first. */
    public PageResponse<UserResponse> getAllUsers(UserFilter filter, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("userId"));
        SearchRules.checkBalanceRange(filter.minBalance(), filter.maxBalance());
        return PageResponse.from(userRepository.search(filter, pageable).map(UserResponse::from));
    }

    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    /** A customer without a password, who can't log in until one is set (used by tests and tools). */
    public UserResponse createUser(String name, String email, Address address) {
        return registerUser(name, email, address, null, Role.CUSTOMER);
    }

    /**
     * Creates a user with an already-hashed password and a role. The email check gives a
     * clear error in the normal case; if two requests race, the unique index still rejects one.
     */
    public UserResponse registerUser(String name, String email, Address address, String passwordHash, Role role) {
        String normalizedEmail = normalizeEmail(email);
        AuditDetails audit = AuditDetails.none().withDetails("email " + normalizedEmail + ", role " + role);
        return auditService.recordFailures(AuditAction.USER_CREATED, audit, () -> transactions.run(() -> {
            if (userRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException(normalizedEmail);
            }
            User user = userRepository.save(
                    new User(name.trim(), normalizedEmail, normalizeAddress(address), passwordHash, role));
            auditService.recordSuccess(AuditAction.USER_CREATED, audit.withUserId(user.getUserId()), List.of());
            return UserResponse.from(user);
        }));
    }

    public UserResponse updateUser(Long userId, String name, String email, Address address) {
        AuditDetails audit = AuditDetails.forUser(userId);
        return auditService.recordFailures(AuditAction.USER_UPDATED, audit, () -> transactions.run(() -> {
            User user = findUser(userId);
            String newName = name.trim();
            String normalizedEmail = normalizeEmail(email);
            Address newAddress = normalizeAddress(address);

            // Keeping your own email is fine; taking someone else's is not
            boolean takenByAnotherUser = userRepository.findByEmail(normalizedEmail)
                    .filter(other -> !other.getUserId().equals(userId))
                    .isPresent();
            if (takenByAnotherUser) {
                throw new DuplicateEmailException(normalizedEmail);
            }

            // Record which fields actually changed
            List<String> changed = new ArrayList<>();
            if (!user.getName().equals(newName)) {
                changed.add("name");
            }
            if (!user.getEmail().equals(normalizedEmail)) {
                changed.add("email");
            }
            if (!Objects.equals(user.getAddress(), newAddress)) {
                changed.add("address");
            }

            // The password hash and role are kept as they are: this update can't change them
            user.setName(newName);
            user.setEmail(normalizedEmail);
            user.setAddress(newAddress);
            User saved = userRepository.save(user);
            auditService.recordSuccess(AuditAction.USER_UPDATED, audit.withDetails(
                    changed.isEmpty() ? "No changes" : "Changed: " + String.join(", ", changed)), List.of());
            return UserResponse.from(saved);
        }));
    }

    /** A user can only be deleted once all of their accounts are gone. */
    public void deleteUser(Long userId) {
        AuditDetails audit = AuditDetails.forUser(userId);
        auditService.recordFailures(AuditAction.USER_DELETED, audit, () -> transactions.run(() -> {
            User user = findUser(userId);
            if (accountRepository.existsByUserId(userId)) {
                throw new OperationNotAllowedException(
                        "User " + userId + " still has accounts. Delete their accounts first.");
            }
            userRepository.delete(user);
            auditService.recordSuccess(AuditAction.USER_DELETED,
                    audit.withDetails("email " + user.getEmail()), List.of());
            return null;
        }));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** Trims every field and stores the state uppercase ("md" becomes "MD"), so searches match. */
    private Address normalizeAddress(Address address) {
        return new Address(
                address.street().trim(),
                address.city().trim(),
                address.state().trim().toUpperCase(),
                address.zip().trim());
    }
}
