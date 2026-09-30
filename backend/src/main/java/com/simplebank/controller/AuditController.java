package com.simplebank.controller;

import com.simplebank.dto.AuditEventResponse;
import com.simplebank.dto.PageResponse;
import com.simplebank.model.AuditAction;
import com.simplebank.model.AuditOutcome;
import com.simplebank.repository.AuditFilter;
import com.simplebank.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Read-only access to the audit trail, for fraud and loss prevention and compliance:
 * who did what, when, to which accounts, for how much, and whether it succeeded.
 * There are no POST, PUT, or DELETE endpoints: audit events are only created by the app itself.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Search, newest first, e.g.
     * /api/audit?accountId=3&action=TRANSFER&outcome=REJECTED&from=2026-09-01T00:00:00Z
     * accountId matches either side of a transfer. from and to are UTC timestamps.
     */
    @GetMapping
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditService.search(new AuditFilter(accountId, userId, action, outcome, from, to), page, size);
    }

    @GetMapping("/{auditId}")
    public AuditEventResponse getEvent(@PathVariable Long auditId) {
        return auditService.get(auditId);
    }

    @GetMapping("/reference/{referenceId}")
    public AuditEventResponse getByReference(@PathVariable String referenceId) {
        return auditService.getByReference(referenceId);
    }

    /** Trace one history record (from /api/accounts/{id}/transactions) back to its audit event. */
    @GetMapping("/transactions/{txnId}")
    public AuditEventResponse getByTransaction(@PathVariable Long txnId) {
        return auditService.getByTransaction(txnId);
    }
}
