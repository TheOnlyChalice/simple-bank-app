package com.simplebank.audit;

import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import com.simplebank.dto.AuditEventResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.AccountType;
import com.simplebank.model.AuditAction;
import com.simplebank.model.AuditOutcome;
import com.simplebank.repository.AuditFilter;
import com.simplebank.service.AccountService;
import com.simplebank.service.AuditService;
import com.simplebank.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit trail: every change and every rejected attempt is recorded with who, when,
 * which accounts, how much, and why, and can be traced and searched afterwards.
 */
class AuditTest extends MongoTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private WebApplicationContext context;

    private long userId;
    private long accountId;

    @BeforeEach
    void setUp() {
        userId = userService.createUser("Audit User", "audit@example.com", TestData.ADDRESS).userId();
        accountId = accountService.createAccount(userId, AccountType.SAVINGS).accountId();
    }

    // ----- What gets recorded -----

    @Test
    void successfulDepositIsAuditedWithEveryDetail() {
        accountService.deposit(accountId, new BigDecimal("150"));

        AuditEventResponse event = eventsFor(accountId).get(0); // newest first
        assertThat(event.action()).isEqualTo(AuditAction.DEPOSIT);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.amount()).isEqualByComparingTo("150.00");
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.transactionIds()).hasSize(1);
        assertThat(event.actor()).isEqualTo("anonymous@127.0.0.1"); // Spring tests provide a mock request
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.referenceId()).isNotBlank();
        assertThat(event.reason()).isNull();
        assertThat(event.details()).contains("0.00").contains("150.00");
    }

    @Test
    void rejectedWithdrawalIsAuditedWithTheReason() {
        assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("500")))
                .isInstanceOf(InsufficientFundsException.class);

        AuditEventResponse event = eventsFor(accountId).get(0);
        assertThat(event.action()).isEqualTo(AuditAction.WITHDRAW);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(event.reason()).contains("Insufficient funds");
        assertThat(event.amount()).isEqualByComparingTo("500");
        assertThat(event.transactionIds()).isEmpty(); // nothing was created
    }

    @Test
    void invalidAmountsAreAuditedToo() {
        assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("-5")))
                .isInstanceOf(InvalidAmountException.class);

        AuditEventResponse event = eventsFor(accountId).get(0);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(event.amount()).isEqualByComparingTo("-5");
    }

    @Test
    void transferIsOneEventTraceableFromEitherSide() {
        long otherId = accountService.createAccount(userId, AccountType.CHECKING).accountId();
        accountService.deposit(accountId, new BigDecimal("100"));

        accountService.transfer(accountId, otherId, new BigDecimal("40"));

        AuditEventResponse transfer = eventsFor(accountId).get(0);
        assertThat(transfer.action()).isEqualTo(AuditAction.TRANSFER);
        assertThat(transfer.accountId()).isEqualTo(accountId);
        assertThat(transfer.relatedAccountId()).isEqualTo(otherId);
        assertThat(transfer.transactionIds()).hasSize(2);

        // Both history records trace back to the same single event
        for (Long txnId : transfer.transactionIds()) {
            assertThat(auditService.getByTransaction(txnId).referenceId()).isEqualTo(transfer.referenceId());
        }
        // Searching by the receiving account finds it too
        assertThat(eventsFor(otherId).get(0).referenceId()).isEqualTo(transfer.referenceId());
        // And the reference ID finds it directly
        assertThat(auditService.getByReference(transfer.referenceId()).auditId()).isEqualTo(transfer.auditId());
    }

    @Test
    void auditTrailSurvivesAccountDeletion() {
        accountService.deposit(accountId, new BigDecimal("20"));
        accountService.withdraw(accountId, new BigDecimal("20"));
        accountService.deleteAccount(accountId);

        assertThat(eventsFor(accountId)).extracting(AuditEventResponse::action).containsExactly(
                AuditAction.ACCOUNT_DELETED, AuditAction.WITHDRAW, AuditAction.DEPOSIT, AuditAction.ACCOUNT_CREATED);
    }

    @Test
    void userUpdateRecordsWhichFieldsChanged() {
        userService.updateUser(userId, "Audit User", "new.audit@example.com", TestData.ADDRESS);

        AuditEventResponse event = auditService.search(
                new AuditFilter(null, userId, AuditAction.USER_UPDATED, null, null, null), 0, 10).content().get(0);
        assertThat(event.details()).isEqualTo("Changed: email");
    }

    @Test
    void duplicateEmailIsAuditedAsRejected() {
        assertThatThrownBy(() -> userService.createUser("Copy", "audit@example.com", TestData.ADDRESS))
                .isInstanceOf(DuplicateEmailException.class);

        AuditEventResponse event = auditService.search(
                new AuditFilter(null, null, AuditAction.USER_CREATED, AuditOutcome.REJECTED, null, null), 0, 10)
                .content().get(0);
        assertThat(event.reason()).contains("audit@example.com");
    }

    @Test
    void searchByTimeRangeOnlyFindsLaterEvents() throws InterruptedException {
        Thread.sleep(20);
        Instant start = Instant.now();
        Thread.sleep(20);
        accountService.deposit(accountId, new BigDecimal("10"));

        List<AuditEventResponse> events = auditService.search(
                new AuditFilter(accountId, null, null, null, start, null), 0, 100).content();
        assertThat(events).extracting(AuditEventResponse::action).containsExactly(AuditAction.DEPOSIT);
    }

    @Test
    void unknownAuditEventIsNotFound() {
        assertThatThrownBy(() -> auditService.get(999_999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> auditService.getByTransaction(999_999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Through the API -----

    @Test
    void apiRecordsTheCallersAddressAndSearchesByOutcome() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(post("/api/accounts/" + accountId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 999}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/audit")
                        .param("accountId", String.valueOf(accountId))
                        .param("outcome", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("WITHDRAW"))
                .andExpect(jsonPath("$.content[0].actor").value("anonymous@127.0.0.1"))
                .andExpect(jsonPath("$.content[0].reason", containsString("Insufficient funds")));
    }

    @Test
    void apiTracesATransactionAndRejectsBadFilters() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        accountService.deposit(accountId, new BigDecimal("25"));
        long txnId = accountService.getTransactions(accountId, 0, 1).content().get(0).txnId();

        mockMvc.perform(get("/api/audit/transactions/" + txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("DEPOSIT"))
                .andExpect(jsonPath("$.outcome").value("SUCCESS"));

        mockMvc.perform(get("/api/audit/999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/audit").param("outcome", "MAYBE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/audit")
                        .param("from", "2026-12-31T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from cannot be after to"));
    }

    private List<AuditEventResponse> eventsFor(long account) {
        return auditService.search(new AuditFilter(account, null, null, null, null, null), 0, 100).content();
    }
}
