package com.simplebank.config;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Attaches validation rules (JSON Schema) to each collection at startup. MongoDB then
 * rejects any insert or update that breaks them, even ones that bypass the services.
 * These are the MongoDB equivalent of the CHECK constraints in the old SQL schema.
 *
 * Idempotent: safe to run on every startup. Requires the dbAdmin role on the database.
 * Runs first (@Order 1), before the admin account is created.
 * database/mongo-setup.js contains the same rules for setting up a database by hand.
 */
@Component
@Order(1)
public class MongoSchemaSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoSchemaSetup.class);

    static final String USERS_SCHEMA = """
            {
              "bsonType": "object",
              "required": ["_id", "name", "email", "address", "role", "createdAt"],
              "properties": {
                "_id":          { "bsonType": "long" },
                "name":         { "bsonType": "string", "minLength": 1, "maxLength": 100 },
                "email":        { "bsonType": "string", "maxLength": 100, "pattern": "^[^@\\\\s]+@[^@\\\\s]+$" },
                "address": {
                  "bsonType": "object",
                  "required": ["street", "city", "state", "zip"],
                  "properties": {
                    "street": { "bsonType": "string", "minLength": 1, "maxLength": 100 },
                    "city":   { "bsonType": "string", "minLength": 1, "maxLength": 60 },
                    "state":  { "bsonType": "string", "pattern": "^[A-Z]{2}$" },
                    "zip":    { "bsonType": "string", "pattern": "^\\\\d{5}(-\\\\d{4})?$" }
                  }
                },
                "passwordHash": { "bsonType": "string", "minLength": 20 },
                "role":         { "enum": ["CUSTOMER", "ADMIN"] },
                "createdAt":    { "bsonType": "date" }
              }
            }
            """;

    static final String ACCOUNTS_SCHEMA = """
            {
              "bsonType": "object",
              "required": ["_id", "userId", "balance", "accountType", "createdAt"],
              "properties": {
                "_id":         { "bsonType": "long" },
                "userId":      { "bsonType": "long" },
                "balance":     { "bsonType": "decimal", "minimum": 0 },
                "accountType": { "enum": ["SAVINGS", "CHECKING"] },
                "createdAt":   { "bsonType": "date" }
              }
            }
            """;

    static final String TRANSACTIONS_SCHEMA = """
            {
              "bsonType": "object",
              "required": ["_id", "accountId", "txnType", "amount", "createdAt"],
              "properties": {
                "_id":              { "bsonType": "long" },
                "accountId":        { "bsonType": "long" },
                "txnType":          { "enum": ["DEPOSIT", "WITHDRAW", "TRANSFER_IN", "TRANSFER_OUT"] },
                "amount":           { "bsonType": "decimal", "minimum": 0, "exclusiveMinimum": true },
                "relatedAccountId": { "bsonType": "long" },
                "createdAt":        { "bsonType": "date" }
              },
              "anyOf": [
                {
                  "properties": { "txnType": { "enum": ["TRANSFER_IN", "TRANSFER_OUT"] } },
                  "required": ["relatedAccountId"]
                },
                {
                  "properties": { "txnType": { "enum": ["DEPOSIT", "WITHDRAW"] } },
                  "not": { "required": ["relatedAccountId"] }
                }
              ]
            }
            """;

    /** Append-only audit trail. Anything that isn't a success must say why (reason). */
    static final String AUDIT_SCHEMA = """
            {
              "bsonType": "object",
              "required": ["_id", "referenceId", "timestamp", "actor", "action", "outcome"],
              "properties": {
                "_id":              { "bsonType": "long" },
                "referenceId":      { "bsonType": "string", "minLength": 1 },
                "timestamp":        { "bsonType": "date" },
                "actor":            { "bsonType": "string", "minLength": 1 },
                "action":           { "enum": ["USER_CREATED", "USER_UPDATED", "USER_DELETED",
                                               "ACCOUNT_CREATED", "ACCOUNT_UPDATED", "ACCOUNT_DELETED",
                                               "DEPOSIT", "WITHDRAW", "TRANSFER",
                                               "LOGIN", "ACCESS_DENIED"] },
                "outcome":          { "enum": ["SUCCESS", "REJECTED", "FAILED"] },
                "reason":           { "bsonType": "string" },
                "userId":           { "bsonType": "long" },
                "accountId":        { "bsonType": "long" },
                "relatedAccountId": { "bsonType": "long" },
                "amount":           { "bsonType": "decimal" },
                "transactionIds":   { "bsonType": "array", "items": { "bsonType": "long" } },
                "details":          { "bsonType": "string" }
              },
              "anyOf": [
                { "properties": { "outcome": { "enum": ["SUCCESS"] } } },
                { "required": ["reason"] }
              ]
            }
            """;

    private final MongoTemplate mongoTemplate;

    public MongoSchemaSetup(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        applyRules("users", USERS_SCHEMA);
        applyRules("accounts", ACCOUNTS_SCHEMA);
        applyRules("transactions", TRANSACTIONS_SCHEMA);
        applyRules("audit_log", AUDIT_SCHEMA);
    }

    private void applyRules(String collection, String schemaJson) {
        Document validator = new Document("$jsonSchema", Document.parse(schemaJson));
        MongoDatabase database = mongoTemplate.getDb();

        if (mongoTemplate.collectionExists(collection)) {
            // collMod = "collection modify": replace the existing rules
            database.runCommand(new Document("collMod", collection)
                    .append("validator", validator)
                    .append("validationLevel", "strict")
                    .append("validationAction", "error"));
        } else {
            database.createCollection(collection, new CreateCollectionOptions()
                    .validationOptions(new ValidationOptions()
                            .validator(validator)
                            .validationLevel(ValidationLevel.STRICT)
                            .validationAction(ValidationAction.ERROR)));
        }
        log.info("Validation rules applied to '{}'", collection);
    }
}
