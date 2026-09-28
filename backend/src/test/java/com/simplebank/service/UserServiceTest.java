package com.simplebank.service;

import com.simplebank.dto.UserResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.repository.inmemory.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(new InMemoryUserRepository());
    }

    @Test
    void createUserNormalizesEmail() {
        UserResponse user = userService.createUser("  Jane Doe ", "  Jane@Example.COM ");
        assertThat(user.userId()).isEqualTo(1L);
        assertThat(user.name()).isEqualTo("Jane Doe");
        assertThat(user.email()).isEqualTo("jane@example.com");
    }

    @Test
    void emailMustBeUnique() {
        userService.createUser("Jane", "jane@example.com");
        assertThatThrownBy(() -> userService.createUser("Other Jane", "JANE@example.com"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void unknownUserThrowsNotFound() {
        assertThatThrownBy(() -> userService.getUser(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
