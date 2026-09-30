package com.simplebank.repository;

import com.simplebank.model.AuditEvent;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * The audit log is append-only, so this repository deliberately extends the plain
 * Repository marker instead of MongoRepository: only the methods declared here exist.
 * There is no update or delete, so the app has no way to change or remove an audit event.
 */
public interface AuditRepository extends Repository<AuditEvent, Long>, AuditSearchRepository {

    /** Only ever called with new events (no ID yet), so it always inserts. */
    AuditEvent save(AuditEvent event);

    Optional<AuditEvent> findById(Long auditId);

    Optional<AuditEvent> findByReferenceId(String referenceId);
}
