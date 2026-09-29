package com.simplebank.repository.inmemory;

import com.simplebank.model.User;
import com.simplebank.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Step 1 storage. Data lives in a map and is lost when the app restarts. */
@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1); // acts like AUTO_INCREMENT

    @Override
    public User save(User user) {
        if (user.getUserId() == null) {
            user.setUserId(nextId.getAndIncrement());
        }
        users.put(user.getUserId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return users.values().stream()
                .sorted(Comparator.comparing(User::getUserId))
                .toList();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public void deleteById(Long userId) {
        users.remove(userId);
    }
}
