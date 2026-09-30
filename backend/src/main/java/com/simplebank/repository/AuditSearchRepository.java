package com.simplebank.repository;

import com.simplebank.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/** Custom queries added to AuditRepository. Implemented by AuditSearchRepositoryImpl. */
public interface AuditSearchRepository {

    Page<AuditEvent> search(AuditFilter filter, Pageable pageable);

    /** The event that created a given history record. */
    Optional<AuditEvent> findByTransactionId(Long txnId);
}
