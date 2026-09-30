package com.simplebank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One entry in the "audit_log" collection: who did what, when, to which accounts,
 * for how much, and whether it succeeded. Used for fraud and loss prevention and compliance.
 *
 * Audit events are append-only: the app only ever adds them, never changes or deletes them,
 * so the trail survives even after the accounts and users involved are deleted.
 */
@Document(collection = "audit_log")
@CompoundIndexes({
        @CompoundIndex(name = "account_events", def = "{'accountId': 1, '_id': -1}"),
        @CompoundIndex(name = "related_account_events", def = "{'relatedAccountId': 1, '_id': -1}"),
        @CompoundIndex(name = "user_events", def = "{'userId': 1, '_id': -1}")
})
public class AuditEvent {

    @Id
    private Long auditId;

    /** A unique trace ID for this event. */
    @Indexed(unique = true)
    private String referenceId;

    /** When it happened, in UTC. */
    @Indexed
    private Instant timestamp;

    /** Who did it. Until login exists (Step 3): "anonymous@<IP address>" or "system". */
    private String actor;

    private AuditAction action;

    private AuditOutcome outcome;

    /** Why it was rejected or failed. Null for successes. */
    private String reason;

    private Long userId;

    /** The main account involved (for a transfer, the one money left). */
    private Long accountId;

    /** For a transfer, the account money went to. */
    private Long relatedAccountId;

    /** The amount requested, recorded even when the request was rejected. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    /** The history records this action created (a transfer creates two). */
    @Indexed
    private List<Long> transactionIds;

    /** Extra context, e.g. the balance before and after. */
    private String details;

    public AuditEvent() {
    }

    public AuditEvent(AuditAction action, AuditOutcome outcome, String actor, Long userId, Long accountId,
                      Long relatedAccountId, BigDecimal amount, List<Long> transactionIds,
                      String reason, String details) {
        this.referenceId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.action = action;
        this.outcome = outcome;
        this.actor = actor;
        this.userId = userId;
        this.accountId = accountId;
        this.relatedAccountId = relatedAccountId;
        this.amount = amount;
        this.transactionIds = transactionIds == null ? List.of() : List.copyOf(transactionIds);
        this.reason = reason;
        this.details = details;
    }

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public String getReferenceId() { return referenceId; }
    public Instant getTimestamp() { return timestamp; }
    public String getActor() { return actor; }
    public AuditAction getAction() { return action; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public Long getUserId() { return userId; }
    public Long getAccountId() { return accountId; }
    public Long getRelatedAccountId() { return relatedAccountId; }
    public BigDecimal getAmount() { return amount; }
    public List<Long> getTransactionIds() { return transactionIds; }
    public String getDetails() { return details; }
}
