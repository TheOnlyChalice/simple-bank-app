package com.simplebank.repository;

import com.simplebank.model.Account;
import com.simplebank.model.Transaction;
import com.simplebank.model.User;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.SessionSynchronization;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Gives new users, accounts, and transactions numeric IDs (1, 2, 3...), like
 * MySQL's AUTO_INCREMENT, so the API is unchanged. MongoDB's own IDs are long codes.
 *
 * The "counters" collection holds one document per collection, e.g. { _id: "users", seq: 7 }.
 * Each new ID is an atomic increment of that number ($inc), so two requests can never get the same one.
 *
 * Counters are updated outside any transaction on purpose: otherwise every request that
 * creates something would conflict on the same counter document. If a transaction is
 * rolled back, its ID is simply skipped, just as with AUTO_INCREMENT.
 */
@Component
public class SequenceIdAssigner implements BeforeConvertCallback<Object> {

    private static final String COUNTERS = "counters";

    private final MongoTemplate counters;

    /**
     * Uses its own small MongoTemplate built from the connection only. Asking Spring for its
     * converter here would create a loop, because the converter itself looks up this callback.
     */
    public SequenceIdAssigner(MongoDatabaseFactory databaseFactory) {
        this.counters = new MongoTemplate(databaseFactory);
        this.counters.setSessionSynchronization(SessionSynchronization.NEVER); // never join a transaction
    }

    /** Runs just before a document is saved. Only documents without an ID get a new one. */
    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        if (entity instanceof User user && user.getUserId() == null) {
            user.setUserId(nextId(collection));
        } else if (entity instanceof Account account && account.getAccountId() == null) {
            account.setAccountId(nextId(collection));
        } else if (entity instanceof Transaction txn && txn.getTxnId() == null) {
            txn.setTxnId(nextId(collection));
        }
        return entity;
    }

    private long nextId(String collection) {
        Document counter = counters.findAndModify(
                Query.query(where("_id").is(collection)),
                new Update().inc("seq", 1L),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                COUNTERS);
        return ((Number) counter.get("seq")).longValue();
    }
}
