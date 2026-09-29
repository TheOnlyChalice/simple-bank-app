package com.simplebank.repository.inmemory;

import com.simplebank.model.Account;
import com.simplebank.repository.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
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

    @Override
    public List<Account> findAll() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(Account::getAccountId))
                .toList();
    }

    @Override
    public List<Account> findByUserId(Long userId) {
        return accounts.values().stream()
                .filter(account -> account.getUserId().equals(userId))
                .sorted(Comparator.comparing(Account::getAccountId))
                .toList();
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return accounts.values().stream()
                .anyMatch(account -> account.getUserId().equals(userId));
    }

    @Override
    public void deleteById(Long accountId) {
        accounts.remove(accountId);
    }
}
