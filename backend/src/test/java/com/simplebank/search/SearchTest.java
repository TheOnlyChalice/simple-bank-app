package com.simplebank.search;

import com.simplebank.MongoTestBase;
import com.simplebank.dto.AccountResponse;
import com.simplebank.dto.PageResponse;
import com.simplebank.dto.UserResponse;
import com.simplebank.exception.InvalidRequestException;
import com.simplebank.model.AccountType;
import com.simplebank.model.Address;
import com.simplebank.repository.AccountFilter;
import com.simplebank.repository.BalanceMode;
import com.simplebank.repository.UserFilter;
import com.simplebank.service.AccountService;
import com.simplebank.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Searching users by address and balance, and accounts by balance and type.
 *
 * The cast of customers:
 *   Alice  Baltimore, MD 21201   SAVINGS $60 + SAVINGS $60   total $120 (no single account >= $100)
 *   Bob    Towson, MD 21204      CHECKING $150               total $150
 *   Carol  Arlington, VA 22201   SAVINGS $50                 total $50
 *   Dan    Baltimore, MD 21202   no accounts                 total $0
 */
class SearchTest extends MongoTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        long alice = user("Alice", "Baltimore", "MD", "21201");
        account(alice, AccountType.SAVINGS, "60");
        account(alice, AccountType.SAVINGS, "60");

        long bob = user("Bob", "Towson", "MD", "21204");
        account(bob, AccountType.CHECKING, "150");

        long carol = user("Carol", "Arlington", "VA", "22201");
        account(carol, AccountType.SAVINGS, "50");

        user("Dan", "Baltimore", "MD", "21202");
    }

    // ----- Users by address -----

    @Test
    void usersByState() {
        assertThat(userNames(filter("md", null, null, null, null, null)))
                .containsExactly("Alice", "Bob", "Dan");
    }

    @Test
    void usersByCityIgnoresCase() {
        assertThat(userNames(filter(null, "BALTIMORE", null, null, null, null)))
                .containsExactly("Alice", "Dan");
    }

    @Test
    void usersByZip() {
        assertThat(userNames(filter(null, null, "21204", null, null, null)))
                .containsExactly("Bob");
    }

    // ----- Users by balance -----

    @Test
    void totalModeAddsUpAllAccounts() {
        // Alice's two $60 accounts add up to $120
        assertThat(userNames(filter(null, null, null, "100", null, BalanceMode.TOTAL)))
                .containsExactly("Alice", "Bob");
    }

    @Test
    void anyAccountModeNeedsOneAccountInRange() {
        // Neither of Alice's accounts reaches $100 on its own
        assertThat(userNames(filter(null, null, null, "100", null, BalanceMode.ANY_ACCOUNT)))
                .containsExactly("Bob");
    }

    @Test
    void totalModeCountsUsersWithNoAccountsAsZero() {
        assertThat(userNames(filter(null, null, null, null, "100", BalanceMode.TOTAL)))
                .containsExactly("Carol", "Dan");
    }

    @Test
    void filtersCombine() {
        assertThat(userNames(filter("MD", null, null, "100", null, null)))
                .containsExactly("Alice", "Bob");
        assertThat(userNames(filter("MD", "Baltimore", null, "100", null, null)))
                .containsExactly("Alice");
    }

    @Test
    void filteredUsersArePaged() {
        PageResponse<UserResponse> secondPage = userService.getAllUsers(filter("MD", null, null, null, null, null), 1, 2);

        assertThat(secondPage.content()).extracting(UserResponse::name).containsExactly("Dan");
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.last()).isTrue();
    }

    @Test
    void invalidBalanceRangesAreRejected() {
        assertThatThrownBy(() -> userService.getAllUsers(filter(null, null, null, "200", "100", null), 0, 20))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> userService.getAllUsers(filter(null, null, null, "-5", null, null), 0, 20))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ----- Accounts -----

    @Test
    void accountsByBalanceRange() {
        List<AccountResponse> accounts = accountService.getAllAccounts(
                new AccountFilter(new BigDecimal("55"), new BigDecimal("100"), null), 0, 20).content();

        assertThat(accounts).extracting(AccountResponse::userName).containsExactly("Alice", "Alice");
    }

    @Test
    void accountsByType() {
        List<AccountResponse> accounts = accountService.getAllAccounts(
                new AccountFilter(null, null, AccountType.CHECKING), 0, 20).content();

        assertThat(accounts).extracting(AccountResponse::userName).containsExactly("Bob");
    }

    @Test
    void accountFiltersCombine() {
        List<AccountResponse> accounts = accountService.getAllAccounts(
                new AccountFilter(new BigDecimal("100"), null, AccountType.SAVINGS), 0, 20).content();

        assertThat(accounts).isEmpty(); // Bob's $150 account is CHECKING
    }

    // ----- Through the API -----

    @Test
    void searchUsersThroughTheApi() throws Exception {
        mockMvc.perform(get("/api/users")
                        .param("state", "MD")
                        .param("minBalance", "100")
                        .param("balanceMode", "ANY_ACCOUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Bob"))
                .andExpect(jsonPath("$.content[0].address.city").value("Towson"));
    }

    @Test
    void searchAccountsThroughTheApi() throws Exception {
        mockMvc.perform(get("/api/accounts").param("accountType", "CHECKING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userName").value("Bob"));
    }

    @Test
    void invalidBalanceModeReturns400() throws Exception {
        mockMvc.perform(get("/api/users").param("balanceMode", "SOMETIMES"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("balanceMode")));
    }

    @Test
    void minGreaterThanMaxReturns400() throws Exception {
        mockMvc.perform(get("/api/accounts").param("minBalance", "500").param("maxBalance", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("minBalance cannot be greater than maxBalance"));
    }

    // ----- Helpers -----

    private long user(String name, String city, String state, String zip) {
        return userService.createUser(name, name.toLowerCase() + "@example.com",
                new Address("1 Test St", city, state, zip)).userId();
    }

    private void account(long userId, AccountType type, String balance) {
        long accountId = accountService.createAccount(userId, type).accountId();
        accountService.deposit(accountId, new BigDecimal(balance));
    }

    private static UserFilter filter(String state, String city, String zip,
                                     String minBalance, String maxBalance, BalanceMode mode) {
        return new UserFilter(state, city, zip,
                minBalance == null ? null : new BigDecimal(minBalance),
                maxBalance == null ? null : new BigDecimal(maxBalance),
                mode);
    }

    private List<String> userNames(UserFilter filter) {
        return userService.getAllUsers(filter, 0, 20).content().stream()
                .map(UserResponse::name)
                .toList();
    }
}
