package com.simplebank.repository;

import com.simplebank.model.Account;

import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(Long accountId);
}
