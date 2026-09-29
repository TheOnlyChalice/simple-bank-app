package com.simplebank.repository;

import com.simplebank.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB generates the implementation at startup.
 * MongoRepository provides save, findById, findAll, delete, and more;
 * the methods below are derived queries built from their names.
 */
public interface UserRepository extends MongoRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
