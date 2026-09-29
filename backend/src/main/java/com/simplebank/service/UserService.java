package com.simplebank.service;

import com.simplebank.dto.UserResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.User;
import com.simplebank.repository.AccountRepository;
import com.simplebank.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public UserService(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    /** synchronized so two requests can't register the same email at once. */
    public synchronized UserResponse createUser(String name, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }
        User user = userRepository.save(new User(name.trim(), normalizedEmail));
        return UserResponse.from(user);
    }

    public synchronized UserResponse updateUser(Long userId, String name, String email) {
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
    public synchronized void deleteUser(Long userId) {
        findUser(userId);
        if (accountRepository.existsByUserId(userId)) {
            throw new OperationNotAllowedException(
                    "User " + userId + " still has accounts. Delete their accounts first.");
        }
        userRepository.deleteById(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
