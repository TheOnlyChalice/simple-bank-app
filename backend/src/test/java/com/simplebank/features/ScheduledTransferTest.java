package com.simplebank.features;

import com.jayway.jsonpath.JsonPath;
import com.simplebank.MongoTestBase;
import com.simplebank.TestData;
import com.simplebank.dto.ScheduledTransferResponse;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.InvalidRequestException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.AccountType;
import com.simplebank.model.Role;
import com.simplebank.model.ScheduledTransferStatus;
import com.simplebank.model.TransactionType;
import com.simplebank.service.AccountService;
import com.simplebank.service.ScheduledTransferService;
import com.simplebank.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transfers scheduled for later. The background job is off in tests; instead, the tests call
 * runDueTransfers(...) with a chosen "now", e.g. two hours ahead, to simulate time passing.
 */
class ScheduledTransferTest extends MongoTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ScheduledTransferService scheduledTransfers;

    private long userId;
    private long fromId;
    private long toId;

    @BeforeEach
    void setUp() {
        userId = userService.createUser("Planner", "planner@example.com", TestData.ADDRESS).userId();
        fromId = accountService.createAccount(userId, AccountType.SAVINGS).accountId();
        toId = accountService.createAccount(userId, AccountType.CHECKING).accountId();
        accountService.deposit(fromId, new BigDecimal("100"));
    }

    // ----- Scheduling -----

    @Test
    void schedulingSavesAPendingTransferWithoutMovingMoney() {
        ScheduledTransferResponse scheduled = schedule("40", inHours(1));

        assertThat(scheduled.status()).isEqualTo(ScheduledTransferStatus.PENDING);
        assertThat(scheduled.ownerUserId()).isEqualTo(userId);
        assertThat(scheduled.amount()).isEqualByComparingTo("40.00");
        assertThat(balanceOf(fromId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(toId)).isEqualByComparingTo("0.00");
    }

    @Test
    void schedulingFollowsTheTransferRulesAndTheTimeLimits() {
        assertThatThrownBy(() -> schedule("40", Instant.now().minus(Duration.ofHours(1))))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("at least 1 minute");
        assertThatThrownBy(() -> schedule("40", Instant.now().plusSeconds(20)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("at least 1 minute");
        assertThatThrownBy(() -> schedule("40", Instant.now().plus(Duration.ofDays(400))))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("at most 1 year");
        assertThatThrownBy(() -> scheduledTransfers.schedule(fromId, fromId, new BigDecimal("40"), inHours(1)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("same account");
        assertThatThrownBy(() -> schedule("0", inHours(1)))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> scheduledTransfers.schedule(fromId, 999_999L, new BigDecimal("40"), inHours(1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Running -----

    @Test
    void transferRunsOnlyOnceItsTimeHasCome() {
        ScheduledTransferResponse scheduled = schedule("40", inHours(1));

        assertThat(scheduledTransfers.runDueTransfers(Instant.now())).isZero(); // not due yet
        assertThat(statusOf(scheduled)).isEqualTo(ScheduledTransferStatus.PENDING);

        assertThat(scheduledTransfers.runDueTransfers(inHours(2))).isEqualTo(1);

        ScheduledTransferResponse ran = scheduledTransfers.get(scheduled.scheduledTransferId());
        assertThat(ran.status()).isEqualTo(ScheduledTransferStatus.COMPLETED);
        assertThat(ran.processedAt()).isNotNull();
        assertThat(balanceOf(fromId)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(toId)).isEqualByComparingTo("40.00");
        assertThat(accountService.getTransactions(fromId, 0, 1).content().get(0).type())
                .isEqualTo(TransactionType.TRANSFER_OUT);
    }

    @Test
    void transferFailsWithTheReasonWhenFundsAreMissingAtRunTime() {
        ScheduledTransferResponse scheduled = schedule("500", inHours(1));

        scheduledTransfers.runDueTransfers(inHours(2));

        ScheduledTransferResponse failed = scheduledTransfers.get(scheduled.scheduledTransferId());
        assertThat(failed.status()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(failed.failureReason()).contains("Insufficient funds");
        assertThat(balanceOf(fromId)).isEqualByComparingTo("100.00");
    }

    @Test
    void transferFailsWhenAnAccountIsFrozenAtRunTime() {
        ScheduledTransferResponse scheduled = schedule("10", inHours(1));
        accountService.freezeAccount(fromId, Role.CUSTOMER);

        scheduledTransfers.runDueTransfers(inHours(2));

        ScheduledTransferResponse failed = scheduledTransfers.get(scheduled.scheduledTransferId());
        assertThat(failed.status()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(failed.failureReason()).contains("frozen");
        assertThat(balanceOf(fromId)).isEqualByComparingTo("100.00");
    }

    @Test
    void cancelledTransfersNeverRun() {
        ScheduledTransferResponse scheduled = schedule("40", inHours(1));

        assertThat(scheduledTransfers.cancel(scheduled.scheduledTransferId()).status())
                .isEqualTo(ScheduledTransferStatus.CANCELLED);
        assertThat(scheduledTransfers.runDueTransfers(inHours(2))).isZero();
        assertThat(balanceOf(fromId)).isEqualByComparingTo("100.00");
        assertThatThrownBy(() -> scheduledTransfers.cancel(scheduled.scheduledTransferId()))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void runningTwiceAtTheSameMomentMovesTheMoneyOnlyOnce() throws Exception {
        schedule("40", inHours(1));

        // Two copies of the job (e.g. two servers) picking up the same transfer at once
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> run = () -> scheduledTransfers.runDueTransfers(inHours(2));
            List<Future<Integer>> results = pool.invokeAll(List.of(run, run));
            int total = 0;
            for (Future<Integer> result : results) {
                total += result.get();
            }
            assertThat(total).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        assertThat(balanceOf(fromId)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(toId)).isEqualByComparingTo("40.00");
        assertThat(accountService.getTransactions(fromId, 0, 100).totalElements()).isEqualTo(2); // deposit + one transfer
    }

    // ----- Through the API -----

    @Test
    void customerSchedulesListsAndCancelsThroughTheApi() throws Exception {
        MockMvc asOwner = mockMvcAs(tokenFor(userId));

        String json = asOwner.perform(post("/api/transfers/scheduled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(fromId, toId, "25", inHours(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Number>read(json, "$.scheduledTransferId").longValue();

        asOwner.perform(get("/api/transfers/scheduled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].scheduledTransferId").value(id));

        asOwner.perform(delete("/api/transfers/scheduled/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void otherCustomersCannotScheduleFromSeeOrCancelSomeoneElsesTransfers() throws Exception {
        ScheduledTransferResponse scheduled = schedule("40", inHours(1));
        long strangerId = userService.createUser("Stranger", "stranger@example.com", TestData.ADDRESS).userId();
        MockMvc asStranger = mockMvcAs(tokenFor(strangerId));

        asStranger.perform(post("/api/transfers/scheduled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(fromId, toId, "10", inHours(1))))
                .andExpect(status().isForbidden());
        asStranger.perform(get("/api/transfers/scheduled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0))); // only their own
        asStranger.perform(delete("/api/transfers/scheduled/" + scheduled.scheduledTransferId()))
                .andExpect(status().isForbidden());

        adminMockMvc().perform(get("/api/transfers/scheduled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))); // staff see everyone's
    }

    @Test
    void missingTimeIsAValidationError() throws Exception {
        mockMvcAs(tokenFor(userId)).perform(post("/api/transfers/scheduled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\": " + fromId + ", \"toAccountId\": " + toId + ", \"amount\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.scheduledFor").value("scheduledFor is required"));
    }

    // ----- Helpers -----

    private ScheduledTransferResponse schedule(String amount, Instant when) {
        return scheduledTransfers.schedule(fromId, toId, new BigDecimal(amount), when);
    }

    private ScheduledTransferStatus statusOf(ScheduledTransferResponse transfer) {
        return scheduledTransfers.get(transfer.scheduledTransferId()).status();
    }

    private BigDecimal balanceOf(long accountId) {
        return accountService.getAccount(accountId).balance();
    }

    private static Instant inHours(int hours) {
        return Instant.now().plus(Duration.ofHours(hours));
    }

    private static String scheduleJson(long from, long to, String amount, Instant when) {
        return "{\"fromAccountId\": " + from + ", \"toAccountId\": " + to + ", \"amount\": " + amount
                + ", \"scheduledFor\": \"" + when + "\"}";
    }
}
