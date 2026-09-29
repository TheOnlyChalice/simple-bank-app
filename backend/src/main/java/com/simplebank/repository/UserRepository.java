package com.simplebank.repository;

import com.simplebank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA generates the implementation at startup.
 * JpaRepository provides save, findById, findAll, deleteById, and more;
 * the methods below are "derived queries": Spring builds the SQL from the method name.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
