package com.simplebank.repository;

import com.simplebank.model.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(Long accountId);

    List<Account> findAll();

    /** All accounts owned by one user (one user -> many accounts). */
    List<Account> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteById(Long accountId);
}
