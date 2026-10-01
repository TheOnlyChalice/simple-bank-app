package com.simplebank.features;

import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import com.simplebank.dto.AuditEventResponse;
import com.simplebank.exception.AccountFrozenException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.model.AccountType;
import com.simplebank.model.AuditAction;
import com.simplebank.model.AuditOutcome;
import com.simplebank.model.Role;
import com.simplebank.repository.AuditFilter;
import com.simplebank.service.AccountService;
import com.simplebank.service.AuditService;
import com.simplebank.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Freezing an account: no money moves in or out until it's unfrozen. A customer can undo
 * their own freeze, but only staff can undo a freeze made by the bank.
 */
class FreezeTest extends MongoTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuditService auditService;

    private long userId;
    private long accountId;
    private long otherAccountId;

    @BeforeEach
    void setUp() {
        userId = userService.createUser("Freeze Test", "freeze@example.com", TestData.ADDRESS).userId();
        accountId = accountService.createAccount(userId, AccountType.SAVINGS).accountId();
        otherAccountId = accountService.createAccount(userId, AccountType.CHECKING).accountId();
        accountService.deposit(accountId, new BigDecimal("100"));
        accountService.deposit(otherAccountId, new BigDecimal("50"));
    }

    // ----- The rules -----

    @Test
    void freezingIsShownOnTheAccount() {
        var frozen = accountService.freezeAccount(accountId, Role.CUSTOMER);

        assertThat(frozen.frozen()).isTrue();
        assertThat(frozen.frozenBy()).isEqualTo(Role.CUSTOMER);
        assertThat(frozen.frozenAt()).isNotNull();
    }

    @Test
    void frozenAccountBlocksDepositsAndWithdrawals() {
        accountService.freezeAccount(accountId, Role.CUSTOMER);

        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("10")))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessageContaining("frozen");
        assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("10")))
                .isInstanceOf(AccountFrozenException.class);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void frozenAccountBlocksTransfersInBothDirections() {
        accountService.freezeAccount(accountId, Role.CUSTOMER);

        assertThatThrownBy(() -> accountService.transfer(accountId, otherAccountId, new BigDecimal("10")))
                .isInstanceOf(AccountFrozenException.class);
        assertThatThrownBy(() -> accountService.transfer(otherAccountId, accountId, new BigDecimal("10")))
                .isInstanceOf(AccountFrozenException.class);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(otherAccountId)).isEqualByComparingTo("50.00");
    }

    @Test
    void unfreezingAllowsMoneyToMoveAgain() {
        accountService.freezeAccount(accountId, Role.CUSTOMER);
        var unfrozen = accountService.unfreezeAccount(accountId, Role.CUSTOMER);

        assertThat(unfrozen.frozen()).isFalse();
        assertThat(unfrozen.frozenBy()).isNull();
        accountService.deposit(accountId, new BigDecimal("25"));
        assertThat(balanceOf(accountId)).isEqualByComparingTo("125.00");
    }

    @Test
    void onlyStaffCanUndoAFreezeMadeByTheBank() {
        accountService.freezeAccount(accountId, Role.ADMIN);

        assertThatThrownBy(() -> accountService.unfreezeAccount(accountId, Role.CUSTOMER))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessageContaining("frozen by the bank");
        assertThat(accountService.unfreezeAccount(accountId, Role.ADMIN).frozen()).isFalse();
    }

    @Test
    void cannotFreezeTwiceOrUnfreezeAnAccountThatIsNotFrozen() {
        assertThatThrownBy(() -> accountService.unfreezeAccount(accountId, Role.CUSTOMER))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessageContaining("is not frozen");

        accountService.freezeAccount(accountId, Role.CUSTOMER);
        assertThatThrownBy(() -> accountService.freezeAccount(accountId, Role.CUSTOMER))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessageContaining("already frozen");
    }

    @Test
    void frozenAccountCannotBeClosed() {
        accountService.withdraw(accountId, new BigDecimal("100")); // empty it first
        accountService.freezeAccount(accountId, Role.CUSTOMER);

        assertThatThrownBy(() -> accountService.deleteAccount(accountId))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessageContaining("Unfreeze it before closing it");
    }

    @Test
    void freezingAndBlockedAttemptsAreAudited() {
        accountService.freezeAccount(accountId, Role.CUSTOMER);
        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("10")))
                .isInstanceOf(AccountFrozenException.class);

        List<AuditEventResponse> events = auditService.search(
                new AuditFilter(accountId, null, null, null, null, null), 0, 100).content();
        AuditEventResponse blocked = events.get(0); // newest first
        assertThat(blocked.action()).isEqualTo(AuditAction.DEPOSIT);
        assertThat(blocked.outcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(blocked.reason()).contains("frozen");
        assertThat(events).extracting(AuditEventResponse::action).contains(AuditAction.ACCOUNT_FROZEN);
    }

    // ----- Through the API -----

    @Test
    void customerFreezesAndUnfreezesTheirOwnAccount() throws Exception {
        MockMvc asOwner = mockMvcAs(tokenFor(userId));

        asOwner.perform(post("/api/accounts/" + accountId + "/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(true))
                .andExpect(jsonPath("$.frozenBy").value("CUSTOMER"));

        asOwner.perform(post("/api/accounts/" + accountId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("frozen")));

        asOwner.perform(post("/api/accounts/" + accountId + "/unfreeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(false));
    }

    @Test
    void customerCannotLiftABankFreezeButStaffCan() throws Exception {
        adminMockMvc().perform(post("/api/accounts/" + accountId + "/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozenBy").value("ADMIN"));

        mockMvcAs(tokenFor(userId)).perform(post("/api/accounts/" + accountId + "/unfreeze"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("frozen by the bank")));

        adminMockMvc().perform(post("/api/accounts/" + accountId + "/unfreeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(false));
    }

    @Test
    void customersCannotFreezeSomeoneElsesAccount() throws Exception {
        long strangerId = userService.createUser("Stranger", "stranger@example.com", TestData.ADDRESS).userId();

        mockMvcAs(tokenFor(strangerId)).perform(post("/api/accounts/" + accountId + "/freeze"))
                .andExpect(status().isForbidden());
        assertThat(accountService.getAccount(accountId).frozen()).isFalse();
    }

    private BigDecimal balanceOf(long id) {
        return accountService.getAccount(id).balance();
    }
}
