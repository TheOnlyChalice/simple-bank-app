package com.simplebank.repository;

import com.simplebank.model.User;

import java.util.List;
import java.util.Optional;

/**
 * The service layer only depends on this interface.
 * Step 1: implemented in memory (InMemoryUserRepository).
 * Step 2: becomes "extends JpaRepository<User, Long>" -- the method names
 * below already follow Spring Data naming, so the services won't change.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long userId);

    Optional<User> findByEmail(String email);

    List<User> findAll();

    boolean existsByEmail(String email);

    void deleteById(Long userId);
}
