package com.simplebank.repository;

import com.simplebank.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** All accounts owned by one user (one user -> many accounts), oldest first. */
    List<Account> findByUserIdOrderByAccountIdAsc(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Loads an account and locks its row until the current transaction ends
     * (SELECT ... FOR UPDATE). Any other request that wants to change the same
     * account waits, so two withdrawals can't both spend the same money.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);
}
