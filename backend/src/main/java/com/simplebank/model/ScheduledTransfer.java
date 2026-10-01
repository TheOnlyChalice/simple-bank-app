package com.simplebank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A document in the "scheduled_transfers" collection: a transfer to run at a chosen
 * date and time. Times are Instants (UTC), so they mean the same moment everywhere.
 *
 * The "due" index lets the background job find "PENDING transfers whose time has come"
 * without scanning the collection.
 */
@Document(collection = "scheduled_transfers")
@CompoundIndexes({
        @CompoundIndex(name = "due", def = "{'status': 1, 'scheduledFor': 1}"),
        @CompoundIndex(name = "owner_newest", def = "{'ownerUserId': 1, '_id': -1}")
})
public class ScheduledTransfer {

    @Id
    private Long scheduledTransferId;

    /** The owner of the account the money comes from; they can see and cancel it. */
    private Long ownerUserId;

    private Long fromAccountId;

    private Long toAccountId;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    /** When it should run (UTC). */
    private Instant scheduledFor;

    private ScheduledTransferStatus status;

    private Instant createdAt;

    /** When it completed, failed, or was cancelled. */
    private Instant processedAt;

    /** Why it failed, e.g. "Insufficient funds: ...". Only for FAILED. */
    private String failureReason;

    public ScheduledTransfer() {
    }

    public ScheduledTransfer(Long ownerUserId, Long fromAccountId, Long toAccountId,
                             BigDecimal amount, Instant scheduledFor, Instant createdAt) {
        this.ownerUserId = ownerUserId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.scheduledFor = scheduledFor;
        this.status = ScheduledTransferStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void markCompleted(Instant when) {
        this.status = ScheduledTransferStatus.COMPLETED;
        this.processedAt = when;
    }

    public void markFailed(String reason, Instant when) {
        this.status = ScheduledTransferStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = when;
    }

    public void markCancelled(Instant when) {
        this.status = ScheduledTransferStatus.CANCELLED;
        this.processedAt = when;
    }

    public boolean isPending() {
        return status == ScheduledTransferStatus.PENDING;
    }

    public Long getScheduledTransferId() { return scheduledTransferId; }
    public void setScheduledTransferId(Long scheduledTransferId) { this.scheduledTransferId = scheduledTransferId; }

    public Long getOwnerUserId() { return ownerUserId; }
    public Long getFromAccountId() { return fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getScheduledFor() { return scheduledFor; }
    public ScheduledTransferStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public String getFailureReason() { return failureReason; }
}
