package com.simplebank.repository;

import com.simplebank.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB generates the implementation at startup.
 * MongoRepository provides save, findById, findAll, delete, and more; the methods
 * below are derived queries; search(...) comes from UserSearchRepository.
 */
public interface UserRepository extends MongoRepository<User, Long>, UserSearchRepository {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
