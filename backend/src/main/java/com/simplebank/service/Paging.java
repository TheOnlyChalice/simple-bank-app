package com.simplebank.service;

import com.simplebank.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Checks the page and size a client asked for and builds a Pageable.
 * Shared by every paginated endpoint, so the limits live in one place.
 */
final class Paging {

    /** Largest page a client can ask for. */
    static final int MAX_PAGE_SIZE = 100;

    private Paging() {
    }

    static Pageable of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
