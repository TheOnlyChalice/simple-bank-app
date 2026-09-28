package com.simplebank.repository;

import com.simplebank.model.User;

import java.util.Optional;

/**
 * The service layer only depends on this interface.
 * Step 1: implemented in memory (InMemoryUserRepository).
 * Step 2: becomes "extends JpaRepository<User, Long>".
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long userId);

    boolean existsByEmail(String email);
}
