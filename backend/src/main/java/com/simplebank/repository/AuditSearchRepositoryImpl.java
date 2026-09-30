package com.simplebank.repository;

import com.mongodb.client.MongoCollection;
import com.simplebank.model.AuditEvent;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/** Searches the audit log, newest first. Spring Data plugs this into AuditRepository by its name. */
public class AuditSearchRepositoryImpl implements AuditSearchRepository {

    private static final String AUDIT_LOG = "audit_log";

    private final MongoTemplate mongoTemplate;

    public AuditSearchRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<AuditEvent> search(AuditFilter filter, Pageable pageable) {
        Document query = new Document();
        if (filter.accountId() != null) {
            // Either side of a transfer counts as "involving" the account
            query.append("$or", List.of(
                    new Document("accountId", filter.accountId()),
                    new Document("relatedAccountId", filter.accountId())));
        }
        if (filter.userId() != null) {
            query.append("userId", filter.userId());
        }
        if (filter.action() != null) {
            query.append("action", filter.action().name());
        }
        if (filter.outcome() != null) {
            query.append("outcome", filter.outcome().name());
        }
        if (filter.from() != null || filter.to() != null) {
            Document range = new Document();
            if (filter.from() != null) {
                range.append("$gte", Date.from(filter.from()));
            }
            if (filter.to() != null) {
                range.append("$lte", Date.from(filter.to()));
            }
            query.append("timestamp", range);
        }

        MongoCollection<Document> auditLog = mongoTemplate.getCollection(AUDIT_LOG);
        long count = auditLog.countDocuments(query);

        List<AuditEvent> content = new ArrayList<>();
        auditLog.find(query)
                .sort(new Document("_id", -1))
                .skip((int) pageable.getOffset())
                .limit(pageable.getPageSize())
                .forEach(document -> content.add(mongoTemplate.getConverter().read(AuditEvent.class, document)));

        return new PageImpl<>(content, pageable, count);
    }

    @Override
    public Optional<AuditEvent> findByTransactionId(Long txnId) {
        // Matches when the transactionIds array contains txnId
        Document document = mongoTemplate.getCollection(AUDIT_LOG)
                .find(new Document("transactionIds", txnId))
                .first();
        return Optional.ofNullable(document)
                .map(found -> mongoTemplate.getConverter().read(AuditEvent.class, found));
    }
}
