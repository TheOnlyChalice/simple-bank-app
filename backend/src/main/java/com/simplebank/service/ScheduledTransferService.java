package com.simplebank.service;

import com.simplebank.dto.PageResponse;
import com.simplebank.dto.ScheduledTransferResponse;
import com.simplebank.exception.InvalidRequestException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.Account;
import com.simplebank.model.AuditAction;
import com.simplebank.model.AuditOutcome;
import com.simplebank.model.ScheduledTransfer;
import com.simplebank.model.ScheduledTransferStatus;
import com.simplebank.repository.ScheduledTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Transfers scheduled for a future date and time.
 *
 * Funds are checked when the transfer RUNS, not when it's scheduled (like a scheduled bill
 * payment at a real bank). If the money isn't there, or an account is frozen at that moment,
 * the transfer is marked FAILED with the reason, and never retried.
 *
 * Exactly once: running a transfer marks it COMPLETED inside the SAME MongoDB transaction as
 * the money movement. If two copies of the job (e.g. two servers) pick up the same transfer,
 * both transactions write the same documents, MongoDB aborts one with a write conflict, and
 * its retry sees COMPLETED and does nothing. Cancelling works the same way.
 */
@Service
public class ScheduledTransferService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferService.class);

    /** How far ahead a transfer can be scheduled. */
    static final Duration MIN_LEAD_TIME = Duration.ofMinutes(1);
    static final Duration MAX_LEAD_TIME = Duration.ofDays(365);

    private final ScheduledTransferRepository repository;
    private final AccountService accountService;
    private final MongoTransactions transactions;
    private final AuditService auditService;
    private final Clock clock;

    public ScheduledTransferService(ScheduledTransferRepository repository,
                                    AccountService accountService,
                                    MongoTransactions transactions,
                                    AuditService auditService,
                                    Clock clock) {
        this.repository = repository;
        this.accountService = accountService;
        this.transactions = transactions;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ----- Scheduling and cancelling -----

    public ScheduledTransferResponse schedule(Long fromAccountId, Long toAccountId, BigDecimal amount, Instant scheduledFor) {
        AuditDetails audit = AuditDetails.forTransfer(fromAccountId, toAccountId, amount)
                .withDetails("scheduled for " + scheduledFor);
        return auditService.recordFailures(AuditAction.TRANSFER_SCHEDULED, audit, () -> {
            accountService.checkTransferRequest(fromAccountId, toAccountId, amount); // same rules as a transfer now
            Instant now = clock.instant();
            if (scheduledFor.isBefore(now.plus(MIN_LEAD_TIME))) {
                throw new InvalidRequestException("scheduledFor must be at least 1 minute in the future");
            }
            if (scheduledFor.isAfter(now.plus(MAX_LEAD_TIME))) {
                throw new InvalidRequestException("scheduledFor can be at most 1 year in the future");
            }

            return transactions.run(() -> {
                Account from = accountService.findAccount(fromAccountId); // both accounts must exist (404)
                accountService.findAccount(toAccountId);
                ScheduledTransfer transfer = repository.save(new ScheduledTransfer(
                        from.getUserId(), fromAccountId, toAccountId,
                        amount.setScale(2, RoundingMode.UNNECESSARY), scheduledFor, now));
                auditService.recordSuccess(AuditAction.TRANSFER_SCHEDULED, audit
                        .withUserId(from.getUserId())
                        .withDetails("scheduled transfer #" + transfer.getScheduledTransferId() + " for " + scheduledFor),
                        List.of());
                return ScheduledTransferResponse.from(transfer);
            });
        });
    }

    /** Only PENDING transfers can be cancelled. The record is kept, marked CANCELLED. */
    public ScheduledTransferResponse cancel(Long scheduledTransferId) {
        ScheduledTransfer snapshot = find(scheduledTransferId);
        AuditDetails audit = AuditDetails.forTransfer(snapshot.getFromAccountId(), snapshot.getToAccountId(), snapshot.getAmount())
                .withUserId(snapshot.getOwnerUserId())
                .withDetails("scheduled transfer #" + scheduledTransferId);
        return auditService.recordFailures(AuditAction.SCHEDULED_TRANSFER_CANCELLED, audit, () -> transactions.run(() -> {
            ScheduledTransfer transfer = find(scheduledTransferId);
            if (!transfer.isPending()) {
                throw new OperationNotAllowedException("Scheduled transfer #" + scheduledTransferId + " is already "
                        + transfer.getStatus() + " and can't be cancelled.");
            }
            transfer.markCancelled(clock.instant());
            repository.save(transfer);
            auditService.recordSuccess(AuditAction.SCHEDULED_TRANSFER_CANCELLED, audit, List.of());
            return ScheduledTransferResponse.from(transfer);
        }));
    }

    // ----- Reading -----

    public ScheduledTransferResponse get(Long scheduledTransferId) {
        return ScheduledTransferResponse.from(find(scheduledTransferId));
    }

    /** Newest first. ownerUserId = null lists everyone's (staff only); status = null lists every status. */
    public PageResponse<ScheduledTransferResponse> list(Long ownerUserId, ScheduledTransferStatus status, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "scheduledTransferId"));
        Page<ScheduledTransfer> result;
        if (ownerUserId != null && status != null) {
            result = repository.findByOwnerUserIdAndStatus(ownerUserId, status, pageable);
        } else if (ownerUserId != null) {
            result = repository.findByOwnerUserId(ownerUserId, pageable);
        } else if (status != null) {
            result = repository.findByStatus(status, pageable);
        } else {
            result = repository.findAll(pageable);
        }
        return PageResponse.from(result.map(ScheduledTransferResponse::from));
    }

    // ----- Running due transfers (called by ScheduledTransferRunner, and by tests) -----

    /** Runs every PENDING transfer due at 'now' (in batches of 50). Returns how many completed or failed. */
    public int runDueTransfers(Instant now) {
        List<ScheduledTransfer> due = repository
                .findTop50ByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(ScheduledTransferStatus.PENDING, now);
        int processed = 0;
        for (ScheduledTransfer transfer : due) {
            if (runOne(transfer.getScheduledTransferId())) {
                processed++;
            }
        }
        return processed;
    }

    /** Returns true if the transfer completed or failed; false if it was skipped or will be retried. */
    boolean runOne(Long scheduledTransferId) {
        ScheduledTransfer snapshot = repository.findById(scheduledTransferId).orElse(null);
        if (snapshot == null || !snapshot.isPending()) {
            return false;
        }
        String note = "scheduled transfer #" + scheduledTransferId;
        AuditDetails audit = AuditDetails.forTransfer(snapshot.getFromAccountId(), snapshot.getToAccountId(), snapshot.getAmount())
                .withUserId(snapshot.getOwnerUserId())
                .withDetails(note);
        try {
            Boolean ran = auditService.recordFailures(AuditAction.TRANSFER, audit, () -> transactions.run(() -> {
                ScheduledTransfer transfer = find(scheduledTransferId);
                if (!transfer.isPending()) {
                    return false; // already run or cancelled by someone else
                }
                accountService.moveMoney(transfer.getFromAccountId(), transfer.getToAccountId(),
                        transfer.getAmount(), audit, note);
                transfer.markCompleted(clock.instant());
                repository.save(transfer); // in the same transaction as the money movement
                return true;
            }));
            return Boolean.TRUE.equals(ran);
        } catch (RuntimeException error) {
            if (AuditService.outcomeFor(error) == AuditOutcome.REJECTED) {
                markFailed(scheduledTransferId, error.getMessage()); // a rule said no: don't retry
                return true;
            }
            // A system problem (e.g. the database was unreachable): leave it PENDING to try again next run
            log.warn("Scheduled transfer #{} will be retried: {}", scheduledTransferId, error.getMessage());
            return false;
        }
    }

    private void markFailed(Long scheduledTransferId, String reason) {
        transactions.run(() -> {
            repository.findById(scheduledTransferId)
                    .filter(ScheduledTransfer::isPending)
                    .ifPresent(transfer -> {
                        transfer.markFailed(reason, clock.instant());
                        repository.save(transfer);
                    });
            return null;
        });
    }

    private ScheduledTransfer find(Long scheduledTransferId) {
        return repository.findById(scheduledTransferId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled transfer", scheduledTransferId));
    }
}
