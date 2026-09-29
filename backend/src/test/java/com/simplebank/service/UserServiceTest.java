package com.simplebank.service;

import com.simplebank.dto.UserResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the H2 test database (src/test/resources/application.properties).
 * @Transactional rolls back every test's changes, so each test starts with an empty database.
 */
@SpringBootTest
@Transactional
class UserServiceTest {

    private static final Long UNKNOWN_ID = 999_999L;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    // ----- Create / Read -----

    @Test
    void createUserNormalizesEmail() {
        UserResponse user = userService.createUser("  Jane Doe ", "  Jane@Example.COM ");
        assertThat(user.userId()).isNotNull();
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
        assertThatThrownBy(() -> userService.getUser(UNKNOWN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllUsersReturnsEveryUserInIdOrder() {
        userService.createUser("Jane", "jane@example.com");
        userService.createUser("John", "john@example.com");

        assertThat(userService.getAllUsers())
                .extracting(UserResponse::name)
                .containsExactly("Jane", "John");
    }

    // ----- Update -----

    @Test
    void updateUserChangesNameAndEmail() {
        Long id = userService.createUser("Jane", "jane@example.com").userId();

        UserResponse updated = userService.updateUser(id, "Jane Smith", "Jane.Smith@Example.com");

        assertThat(updated.name()).isEqualTo("Jane Smith");
        assertThat(updated.email()).isEqualTo("jane.smith@example.com");
        assertThat(userService.getUser(id).name()).isEqualTo("Jane Smith");
    }

    @Test
    void updateUserCanKeepTheirOwnEmail() {
        Long id = userService.createUser("Jane", "jane@example.com").userId();
        UserResponse updated = userService.updateUser(id, "Jane Smith", "jane@example.com");
        assertThat(updated.name()).isEqualTo("Jane Smith");
    }

    @Test
    void updateUserCannotTakeAnotherUsersEmail() {
        userService.createUser("Jane", "jane@example.com");
        Long johnId = userService.createUser("John", "john@example.com").userId();

        assertThatThrownBy(() -> userService.updateUser(johnId, "John", "jane@example.com"))
                .isInstanceOf(DuplicateEmailException.class);
        assertThat(userService.getUser(johnId).email()).isEqualTo("john@example.com");
    }

    @Test
    void updateUnknownUserThrowsNotFound() {
        assertThatThrownBy(() -> userService.updateUser(UNKNOWN_ID, "Nobody", "nobody@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Delete -----

    @Test
    void deleteUserWithoutAccounts() {
        Long id = userService.createUser("Jane", "jane@example.com").userId();

        userService.deleteUser(id);

        assertThatThrownBy(() -> userService.getUser(id))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(userService.getAllUsers()).isEmpty();
    }

    @Test
    void cannotDeleteUserWhoStillHasAccounts() {
        Long id = userService.createUser("Jane", "jane@example.com").userId();
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
