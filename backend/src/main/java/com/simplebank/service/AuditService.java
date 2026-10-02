package com.simplebank.service;

import com.simplebank.dto.AuditEventResponse;
import com.simplebank.dto.PageResponse;
import com.simplebank.exception.DuplicateEmailException;
import com.simplebank.exception.InsufficientFundsException;
import com.simplebank.exception.InvalidAmountException;
import com.simplebank.exception.InvalidCredentialsException;
import com.simplebank.exception.InvalidRequestException;
import com.simplebank.exception.OperationNotAllowedException;
import com.simplebank.exception.ResourceNotFoundException;
import com.simplebank.model.AuditAction;
import com.simplebank.model.AuditEvent;
import com.simplebank.model.AuditOutcome;
import com.simplebank.repository.AuditFilter;
import com.simplebank.repository.AuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.function.Supplier;

/**
 * Writes and reads the audit trail.
 *
 * Successes are recorded with recordSuccess(...) inside the same MongoDB transaction as
 * the change itself, so a change can never be saved without its audit event.
 * Failures are recorded by recordFailures(...) after the failed transaction has been
 * rolled back, so rejected attempts are on record even though nothing changed.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    // ----- Writing -----

    /** Records a successful action. Call it inside the transaction that makes the change. */
    public void recordSuccess(AuditAction action, AuditDetails details, List<Long> transactionIds) {
        auditRepository.save(new AuditEvent(action, AuditOutcome.SUCCESS, currentActor(),
                details.userId(), details.accountId(), details.relatedAccountId(), details.amount(),
                transactionIds, null, details.details()));
    }

    /**
     * Runs the work. If it throws, records the attempt as REJECTED (a business rule said no)
     * or FAILED (anything else), then rethrows, so the caller still gets the same error.
     */
    public <T> T recordFailures(AuditAction action, AuditDetails details, Supplier<T> work) {
        try {
            return work.get();
        } catch (RuntimeException error) {
            recordFailure(action, details, error);
            throw error;
        }
    }

    /** A logged-in user tried to use something that isn't theirs, or an admin-only endpoint. */
    public void recordAccessDenied(Long targetUserId, Long accountId, String reason) {
        try {
            auditRepository.save(new AuditEvent(AuditAction.ACCESS_DENIED, AuditOutcome.REJECTED, currentActor(),
                    targetUserId, accountId, null, null, List.of(), reason, currentRequest()));
        } catch (RuntimeException auditError) {
            log.error("Could not record an access denial in the audit log", auditError);
        }
    }

    private void recordFailure(AuditAction action, AuditDetails details, RuntimeException error) {
        try {
            auditRepository.save(new AuditEvent(action, outcomeFor(error), currentActor(),
                    details.userId(), details.accountId(), details.relatedAccountId(), details.amount(),
                    List.of(), reasonFor(error), details.details()));
        } catch (RuntimeException auditError) {
            // Never hide the original error from the caller because the audit write failed
            log.error("Could not record the failed {} in the audit log", action, auditError);
        }
    }

    /** REJECTED means a business or security rule refused the request; FAILED means something else went wrong. */
    static AuditOutcome outcomeFor(RuntimeException error) {
        boolean ruleRefused = error instanceof InsufficientFundsException
                || error instanceof InvalidAmountException
                || error instanceof InvalidRequestException
                || error instanceof OperationNotAllowedException
                || error instanceof ResourceNotFoundException
                || error instanceof DuplicateEmailException
                || error instanceof InvalidCredentialsException
                || error instanceof AccessDeniedException
                || error instanceof DataIntegrityViolationException;
        return ruleRefused ? AuditOutcome.REJECTED : AuditOutcome.FAILED;
    }

    private static String reasonFor(RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.length() > MAX_REASON_LENGTH ? message.substring(0, MAX_REASON_LENGTH) : message;
    }

    /**
     * Who made the request: the logged-in user, e.g. "ana@example.com (user 5, CUSTOMER)".
     * Before logging in (registering, logging in), the caller's IP address instead.
     * "system" for work that doesn't come from an HTTP request.
     */
    static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return jwt.getClaimAsString("email") + " (user " + jwt.getSubject() + ", "
                    + jwt.getClaimAsString("role") + ")";
        }
        HttpServletRequest request = currentHttpRequest();
        return request != null ? "anonymous@" + callerAddress(request) : "system";
    }

    /**
     * The caller's IP address. On AWS, requests arrive through CloudFront, which puts the
     * real caller's address in the CloudFront-Viewer-Address header ("ip:port").
     * Without that header (e.g. on your own computer), it's the direct connection's address.
     */
    private static String callerAddress(HttpServletRequest request) {
        String viewer = request.getHeader("CloudFront-Viewer-Address");
        if (viewer != null && viewer.lastIndexOf(':') > 0) {
            return viewer.substring(0, viewer.lastIndexOf(':'));
        }
        return request.getRemoteAddr();
    }

    /** e.g. "POST /api/accounts/7/withdraw" */
    private static String currentRequest() {
        HttpServletRequest request = currentHttpRequest();
        return request != null ? request.getMethod() + " " + request.getRequestURI() : null;
    }

    private static HttpServletRequest currentHttpRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    // ----- Reading -----

    /** One page of audit events matching the filter, newest first. */
    public PageResponse<AuditEventResponse> search(AuditFilter filter, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "auditId"));
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("from cannot be after to");
        }
        return PageResponse.from(auditRepository.search(filter, pageable).map(AuditEventResponse::from));
    }

    public AuditEventResponse get(Long auditId) {
        return auditRepository.findById(auditId)
                .map(AuditEventResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event", auditId));
    }

    public AuditEventResponse getByReference(String referenceId) {
        return auditRepository.findByReferenceId(referenceId)
                .map(AuditEventResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event", referenceId));
    }

    /** Traces one history record back to the event that created it. */
    public AuditEventResponse getByTransaction(Long txnId) {
        return auditRepository.findByTransactionId(txnId)
                .map(AuditEventResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event for transaction", txnId));
    }
}
