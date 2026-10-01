package com.simplebank.controller;

import com.simplebank.dto.PageResponse;
import com.simplebank.dto.ScheduleTransferRequest;
import com.simplebank.dto.ScheduledTransferResponse;
import com.simplebank.model.ScheduledTransferStatus;
import com.simplebank.security.AccessGuard;
import com.simplebank.security.CurrentUser;
import com.simplebank.service.ScheduledTransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Transfers scheduled for a future date and time. Like instant transfers, money can only
 * come FROM your own account. Customers see and cancel only their own; staff see all.
 */
@RestController
@RequestMapping("/api/transfers/scheduled")
public class ScheduledTransferController {

    private final ScheduledTransferService scheduledTransfers;
    private final AccessGuard accessGuard;

    public ScheduledTransferController(ScheduledTransferService scheduledTransfers, AccessGuard accessGuard) {
        this.scheduledTransfers = scheduledTransfers;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    public ResponseEntity<ScheduledTransferResponse> schedule(@Valid @RequestBody ScheduleTransferRequest request) {
        accessGuard.requireAccountOwnerOrAdmin(request.fromAccountId());
        ScheduledTransferResponse created = scheduledTransfers.schedule(
                request.fromAccountId(), request.toAccountId(), request.amount(), request.scheduledFor());
        return ResponseEntity.created(URI.create("/api/transfers/scheduled/" + created.scheduledTransferId()))
                .body(created);
    }

    /**
     * Newest first. Customers always get their own; staff get everyone's, or one user's with ?userId=.
     * Optional ?status=PENDING (or COMPLETED, FAILED, CANCELLED).
     */
    @GetMapping
    public PageResponse<ScheduledTransferResponse> list(
            @RequestParam(required = false) ScheduledTransferStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser me = accessGuard.currentUser();
        Long owner = me.isAdmin() ? userId : me.userId();
        return scheduledTransfers.list(owner, status, page, size);
    }

    @GetMapping("/{id}")
    public ScheduledTransferResponse get(@PathVariable Long id) {
        ScheduledTransferResponse transfer = scheduledTransfers.get(id);
        accessGuard.requireSelfOrAdmin(transfer.ownerUserId());
        return transfer;
    }

    /** Cancels a PENDING transfer. Returns it, now CANCELLED. */
    @DeleteMapping("/{id}")
    public ScheduledTransferResponse cancel(@PathVariable Long id) {
        accessGuard.requireSelfOrAdmin(scheduledTransfers.get(id).ownerUserId());
        return scheduledTransfers.cancel(id);
    }
}
