package com.simplebank.service;

import com.simplebank.dto.PageResponse;
import com.simplebank.dto.UserResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.User;
import com.simplebank.repository.AccountRepository;
import com.simplebank.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Each user operation changes a single document, and single-document writes are
 * always atomic in MongoDB, so no multi-document transactions are needed here.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public UserService(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    /** One page of users, oldest first. Pages are numbered from 0. */
    public PageResponse<UserResponse> getAllUsers(int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("userId"));
        return PageResponse.from(userRepository.findAll(pageable).map(UserResponse::from));
    }

    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    /**
     * The email check gives a clear error in the normal case. If two requests race,
     * the unique index on email still rejects the second one.
     */
    public UserResponse createUser(String name, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }
        User user = userRepository.save(new User(name.trim(), normalizedEmail));
        return UserResponse.from(user);
    }

    public UserResponse updateUser(Long userId, String name, String email) {
        User user = findUser(userId);
        String normalizedEmail = normalizeEmail(email);

        // Keeping your own email is fine; taking someone else's is not
        boolean takenByAnotherUser = userRepository.findByEmail(normalizedEmail)
                .filter(other -> !other.getUserId().equals(userId))
                .isPresent();
        if (takenByAnotherUser) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        return UserResponse.from(userRepository.save(user));
    }

    /** A user can only be deleted once all of their accounts are gone. */
    public void deleteUser(Long userId) {
        User user = findUser(userId);
        if (accountRepository.existsByUserId(userId)) {
            throw new OperationNotAllowedException(
                    "User " + userId + " still has accounts. Delete their accounts first.");
        }
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
