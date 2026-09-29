package com.simplebank.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the HTTP layer for /api/accounts and /api/transfers, plus CORS.
 * Runs against H2; @Transactional rolls back each test's changes.
 */
@SpringBootTest
@Transactional
class AccountApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        userId = readId(postJson("/api/users", """
                {"name": "John Doe", "email": "john@example.com"}
                """).andExpect(status().isCreated()), "$.userId");
    }

    // ----- Create / Read -----

    @Test
    void createAccountReturns201WithZeroBalance() throws Exception {
        String json = postJson("/api/accounts", "{\"userId\": " + userId + ", \"accountType\": \"SAVINGS\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/accounts/")))
                .andExpect(jsonPath("$.userName").value("John Doe"))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(money(json, "$.balance")).isEqualByComparingTo("0.00");
    }

    @Test
    void invalidAccountTypeReturns400() throws Exception {
        postJson("/api/accounts", "{\"userId\": " + userId + ", \"accountType\": \"GOLD\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountForUnknownUserReturns404() throws Exception {
        postJson("/api/accounts", "{\"userId\": 999999, \"accountType\": \"SAVINGS\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void listAccountsIsPaged() throws Exception {
        createAccount("SAVINGS");
        createAccount("CHECKING");

        mockMvc.perform(get("/api/accounts").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.last").value(false));
    }

    // ----- Money -----

    @Test
    void depositReturnsUpdatedBalance() throws Exception {
        long accountId = createAccount("SAVINGS");

        String json = postJson("/api/accounts/" + accountId + "/deposit", "{\"amount\": 500}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(money(json, "$.balance")).isEqualByComparingTo("500.00");
    }

    @Test
    void negativeDepositReturns400() throws Exception {
        long accountId = createAccount("SAVINGS");

        postJson("/api/accounts/" + accountId + "/deposit", "{\"amount\": -50}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero"));
    }

    @Test
    void depositWithoutAmountReturns400WithFieldError() throws Exception {
        long accountId = createAccount("SAVINGS");

        postJson("/api/accounts/" + accountId + "/deposit", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").value("amount is required"));
    }

    @Test
    void overdraftReturns400() throws Exception {
        long accountId = createAccount("SAVINGS");

        postJson("/api/accounts/" + accountId + "/withdraw", "{\"amount\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient funds")));
    }

    @Test
    void transferReturnsBothUpdatedAccounts() throws Exception {
        long from = createAccount("SAVINGS");
        long to = createAccount("CHECKING");
        postJson("/api/accounts/" + from + "/deposit", "{\"amount\": 500}").andExpect(status().isOk());

        String json = postJson("/api/transfers",
                "{\"fromAccountId\": " + from + ", \"toAccountId\": " + to + ", \"amount\": 200}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(money(json, "$.fromAccount.balance")).isEqualByComparingTo("300.00");
        assertThat(money(json, "$.toAccount.balance")).isEqualByComparingTo("200.00");
        assertThat(money(json, "$.amount")).isEqualByComparingTo("200.00");
    }

    @Test
    void transferToSameAccountReturns400() throws Exception {
        long accountId = createAccount("SAVINGS");

        postJson("/api/transfers",
                "{\"fromAccountId\": " + accountId + ", \"toAccountId\": " + accountId + ", \"amount\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot transfer to the same account"));
    }

    @Test
    void transferWithMissingFieldsReturns400WithFieldErrors() throws Exception {
        postJson("/api/transfers", "{\"amount\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fromAccountId").exists())
                .andExpect(jsonPath("$.fieldErrors.toAccountId").exists());
    }

    // ----- History -----

    @Test
    void historyIsPagedNewestFirst() throws Exception {
        long accountId = createAccount("SAVINGS");
        for (int amount : new int[] {10, 20, 30}) {
            postJson("/api/accounts/" + accountId + "/deposit", "{\"amount\": " + amount + "}")
                    .andExpect(status().isOk());
        }

        String json = mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(money(json, "$.content[0].amount")).isEqualByComparingTo("30.00");
    }

    @Test
    void historyWithInvalidPageSizeReturns400() throws Exception {
        long accountId = createAccount("SAVINGS");

        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    // ----- Update / Delete -----

    @Test
    void updateAccountTypeReturns200() throws Exception {
        long accountId = createAccount("SAVINGS");

        mockMvc.perform(put("/api/accounts/" + accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": \"CHECKING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("CHECKING"));
    }

    @Test
    void deleteAccountWithMoneyReturns409ButEmptyAccountReturns204() throws Exception {
        long accountId = createAccount("SAVINGS");
        postJson("/api/accounts/" + accountId + "/deposit", "{\"amount\": 5}").andExpect(status().isOk());

        mockMvc.perform(delete("/api/accounts/" + accountId))
                .andExpect(status().isConflict());

        postJson("/api/accounts/" + accountId + "/withdraw", "{\"amount\": 5}").andExpect(status().isOk());
        mockMvc.perform(delete("/api/accounts/" + accountId))
                .andExpect(status().isNoContent());
    }

    // ----- CORS -----

    @Test
    void corsAllowsTheFrontendOrigin() throws Exception {
        mockMvc.perform(get("/api/accounts").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsRejectsUnknownOrigins() throws Exception {
        mockMvc.perform(get("/api/accounts").header("Origin", "http://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    // ----- Helpers -----

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createAccount(String type) throws Exception {
        return readId(postJson("/api/accounts", "{\"userId\": " + userId + ", \"accountType\": \"" + type + "\"}")
                .andExpect(status().isCreated()), "$.accountId");
    }

    private long readId(ResultActions result, String path) throws Exception {
        return JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), path).longValue();
    }

    /** Money is compared as BigDecimal, so 500 and 500.00 count as equal. */
    private BigDecimal money(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
    }
}
