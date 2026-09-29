package com.simplebank.repository;

import com.simplebank.model.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom search added to AccountRepository. Implemented by AccountSearchRepositoryImpl. */
public interface AccountSearchRepository {

    Page<Account> search(AccountFilter filter, Pageable pageable);
}
