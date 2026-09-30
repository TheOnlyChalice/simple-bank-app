package com.simplebank.dto;

import com.simplebank.model.Role;
import com.simplebank.model.User;

import java.time.LocalDateTime;

/** A user as the API shows them. The password hash is deliberately never included. */
public record UserResponse(
        Long userId,
        String name,
        String email,
        AddressDto address,
        Role role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getName(), user.getEmail(),
                AddressDto.from(user.getAddress()), user.getRole(), user.getCreatedAt());
    }
}
