package com.simplebank.repository.inmemory;

import com.simplebank.model.Account;
import com.simplebank.repository.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Account save(Account account) {
        if (account.getAccountId() == null) {
            account.setAccountId(nextId.getAndIncrement());
        }
        accounts.put(account.getAccountId(), account);
        return account;
    }

    @Override
    public Optional<Account> findById(Long accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }
}
