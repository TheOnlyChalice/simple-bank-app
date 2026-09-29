package com.simplebank.repository;

import com.simplebank.model.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AccountRepository extends MongoRepository<Account, Long> {

    /** All accounts owned by one user (one user -> many accounts), oldest first. */
    List<Account> findByUserIdOrderByAccountIdAsc(Long userId);

    boolean existsByUserId(Long userId);
}
