package com.simplebank.service;

import com.simplebank.MongoTestBase;
import com.simplebank.TestData;

import com.simplebank.dto.PageResponse;
import com.simplebank.dto.UserResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against MongoDB in Docker (see MongoTestBase), emptied before each test.
 */
class UserServiceTest extends MongoTestBase {

    private static final Long UNKNOWN_ID = 999_999L;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    // ----- Create / Read -----

    @Test
    void createUserNormalizesEmail() {
        UserResponse user = userService.createUser("  Jane Doe ", "  Jane@Example.COM ", TestData.ADDRESS);
        assertThat(user.userId()).isNotNull();
        assertThat(user.name()).isEqualTo("Jane Doe");
        assertThat(user.email()).isEqualTo("jane@example.com");
    }

    @Test
    void emailMustBeUnique() {
        userService.createUser("Jane", "jane@example.com", TestData.ADDRESS);
        assertThatThrownBy(() -> userService.createUser("Other Jane", "JANE@example.com", TestData.ADDRESS))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void unknownUserThrowsNotFound() {
        assertThatThrownBy(() -> userService.getUser(UNKNOWN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllUsersReturnsEveryUserInIdOrder() {
        userService.createUser("Jane", "jane@example.com", TestData.ADDRESS);
        userService.createUser("John", "john@example.com", TestData.ADDRESS);

        assertThat(userService.getAllUsers(0, 20).content())
                .extracting(UserResponse::name)
                .containsExactly("Jane", "John");
    }

    @Test
    void getAllUsersIsPaged() {
        for (int i = 1; i <= 3; i++) {
            userService.createUser("User " + i, "user" + i + "@example.com", TestData.ADDRESS);
        }

        PageResponse<UserResponse> secondPage = userService.getAllUsers(1, 2);

        assertThat(secondPage.content()).extracting(UserResponse::name).containsExactly("User 3");
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.last()).isTrue();
    }

    // ----- Update -----

    @Test
    void updateUserChangesNameAndEmail() {
        Long id = userService.createUser("Jane", "jane@example.com", TestData.ADDRESS).userId();

        UserResponse updated = userService.updateUser(id, "Jane Smith", "Jane.Smith@Example.com", TestData.ADDRESS);

        assertThat(updated.name()).isEqualTo("Jane Smith");
        assertThat(updated.email()).isEqualTo("jane.smith@example.com");
        assertThat(userService.getUser(id).name()).isEqualTo("Jane Smith");
    }

    @Test
    void updateUserCanKeepTheirOwnEmail() {
        Long id = userService.createUser("Jane", "jane@example.com", TestData.ADDRESS).userId();
        UserResponse updated = userService.updateUser(id, "Jane Smith", "jane@example.com", TestData.ADDRESS);
        assertThat(updated.name()).isEqualTo("Jane Smith");
    }

    @Test
    void updateUserCannotTakeAnotherUsersEmail() {
        userService.createUser("Jane", "jane@example.com", TestData.ADDRESS);
        Long johnId = userService.createUser("John", "john@example.com", TestData.ADDRESS).userId();

        assertThatThrownBy(() -> userService.updateUser(johnId, "John", "jane@example.com", TestData.ADDRESS))
                .isInstanceOf(DuplicateEmailException.class);
        assertThat(userService.getUser(johnId).email()).isEqualTo("john@example.com");
    }

    @Test
    void updateUnknownUserThrowsNotFound() {
        assertThatThrownBy(() -> userService.updateUser(UNKNOWN_ID, "Nobody", "nobody@example.com", TestData.ADDRESS))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Delete -----

    @Test
    void deleteUserWithoutAccounts() {
        Long id = userService.createUser("Jane", "jane@example.com", TestData.ADDRESS).userId();

        userService.deleteUser(id);

        assertThatThrownBy(() -> userService.getUser(id))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(userService.getAllUsers(0, 20).content()).isEmpty();
    }

    @Test
    void cannotDeleteUserWhoStillHasAccounts() {
        Long id = userService.createUser("Jane", "jane@example.com", TestData.ADDRESS).userId();
        accountService.createAccount(id, AccountType.SAVINGS);

        assertThatThrownBy(() -> userService.deleteUser(id))
                .isInstanceOf(OperationNotAllowedException.class);
        assertThat(userService.getUser(id).name()).isEqualTo("Jane");
    }

    @Test
    void deleteUnknownUserThrowsNotFound() {
        assertThatThrownBy(() -> userService.deleteUser(UNKNOWN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
