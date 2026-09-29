package com.simplebank.repository;

import com.simplebank.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom search added to UserRepository. Implemented by UserSearchRepositoryImpl. */
public interface UserSearchRepository {

    Page<User> search(UserFilter filter, Pageable pageable);
}
