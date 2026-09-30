package com.simplebank.audit;

import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import com.simplebank.dto.AccountResponse;
import com.simplebank.exception.InvalidRequestException;
import com.simplebank.model.AccountType;
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

/** Premium accounts: balance at or above a threshold, richest first. */
class PremiumAccountsTest extends MongoTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private WebApplicationContext context;

    private long poor;
    private long rich;
    private long richest;
    private long alsoRich;

    @BeforeEach
    void setUp() {
        long userId = userService.createUser("Premium User", "premium@example.com", TestData.ADDRESS).userId();
        poor = accountWithBalance(userId, "50");
        rich = accountWithBalance(userId, "1000");
        richest = accountWithBalance(userId, "2500");
        alsoRich = accountWithBalance(userId, "1000");
    }

    @Test
    void premiumAccountsAreAtOrAboveTheThresholdRichestFirst() {
        List<AccountResponse> premium = accountService.getPremiumAccounts(new BigDecimal("1000"), 0, 20).content();

        // Equal balances are listed oldest first; 1000 counts because the threshold is inclusive
        assertThat(premium).extracting(AccountResponse::accountId).containsExactly(richest, rich, alsoRich);
        assertThat(premium).extracting(AccountResponse::accountId).doesNotContain(poor);
    }

    @Test
    void thresholdMustBePositive() {
        assertThatThrownBy(() -> accountService.getPremiumAccounts(BigDecimal.ZERO, 0, 20))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> accountService.getPremiumAccounts(null, 0, 20))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void apiReturnsPremiumAccountsAndValidatesTheThreshold() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/api/accounts/premium").param("threshold", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].accountId").value(richest))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/accounts/premium"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'threshold'"));
        mockMvc.perform(get("/api/accounts/premium").param("threshold", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/accounts/premium").param("threshold", "-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("greater than zero")));
    }

    private long accountWithBalance(long userId, String balance) {
        long accountId = accountService.createAccount(userId, AccountType.SAVINGS).accountId();
        accountService.deposit(accountId, new BigDecimal(balance));
        return accountId;
    }
}
