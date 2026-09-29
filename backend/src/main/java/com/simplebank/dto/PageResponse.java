package com.simplebank.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * One page of results, with what a UI needs for "Page 2 of 5" and next/previous buttons.
 * Our own record instead of Spring's Page, so the JSON shape is a stable API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
