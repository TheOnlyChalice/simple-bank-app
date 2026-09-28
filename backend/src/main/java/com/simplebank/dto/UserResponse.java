package com.simplebank.dto;

import com.simplebank.model.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String name,
        String email,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
