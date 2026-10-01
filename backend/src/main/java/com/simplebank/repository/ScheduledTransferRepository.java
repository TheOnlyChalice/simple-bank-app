package com.simplebank.repository;

import com.simplebank.model.ScheduledTransfer;
import com.simplebank.model.ScheduledTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface ScheduledTransferRepository extends MongoRepository<ScheduledTransfer, Long> {

    /** The next batch of transfers whose time has come, oldest due first (uses the "due" index). */
    List<ScheduledTransfer> findTop50ByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(
            ScheduledTransferStatus status, Instant now);

    Page<ScheduledTransfer> findByOwnerUserId(Long ownerUserId, Pageable pageable);

    Page<ScheduledTransfer> findByOwnerUserIdAndStatus(Long ownerUserId, ScheduledTransferStatus status, Pageable pageable);

    Page<ScheduledTransfer> findByStatus(ScheduledTransferStatus status, Pageable pageable);
}
