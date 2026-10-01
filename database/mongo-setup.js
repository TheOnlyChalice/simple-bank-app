// =====================================================================
// Simple Bank Application - MongoDB setup script (mongosh)
//
// The Spring Boot app applies these same rules and indexes automatically
// at startup (MongoSchemaSetup.java and the @Indexed annotations), so this
// script is only needed to set up or inspect a database by hand:
//
//   mongosh "$MONGODB_URI" database/mongo-setup.js
//
// Collections: users, accounts, transactions, audit_log, plus "counters",
// which holds the next numeric ID for each collection (like AUTO_INCREMENT).
// MongoDB has no foreign keys: the app checks that owners exist.
// =====================================================================

const rules = {
  users: {
    bsonType: "object",
    required: ["_id", "name", "email", "address", "role", "createdAt"],
    properties: {
      _id:       { bsonType: "long" },
      name:      { bsonType: "string", minLength: 1, maxLength: 100 },
      email:     { bsonType: "string", maxLength: 100, pattern: "^[^@\\s]+@[^@\\s]+$" },
      address: {                                                   // embedded document
        bsonType: "object",
        required: ["street", "city", "state", "zip"],
        properties: {
          street: { bsonType: "string", minLength: 1, maxLength: 100 },
          city:   { bsonType: "string", minLength: 1, maxLength: 60 },
          state:  { bsonType: "string", pattern: "^[A-Z]{2}$" },            // e.g. "MD"
          zip:    { bsonType: "string", pattern: "^\\d{5}(-\\d{4})?$" }     // 21201 or 21201-1234
        }
      },
      passwordHash: { bsonType: "string", minLength: 20 },            // BCrypt hash, never the password
      role:         { enum: ["CUSTOMER", "ADMIN"] },
      createdAt: { bsonType: "date" }
    }
  },

  accounts: {
    bsonType: "object",
    required: ["_id", "userId", "balance", "accountType", "createdAt"],
    properties: {
      _id:         { bsonType: "long" },
      userId:      { bsonType: "long" },
      balance:     { bsonType: "decimal", minimum: 0 },                 // like CHECK (balance >= 0)
      accountType: { enum: ["SAVINGS", "CHECKING"] },
      createdAt:   { bsonType: "date" },
      frozen:      { bsonType: "bool" },                               // no money moves while frozen
      frozenBy:    { enum: ["CUSTOMER", "ADMIN"] },                    // only staff can lift a bank freeze
      frozenAt:    { bsonType: "date" }
    }
  },

  transactions: {
    bsonType: "object",
    required: ["_id", "accountId", "txnType", "amount", "createdAt"],
    properties: {
      _id:              { bsonType: "long" },
      accountId:        { bsonType: "long" },
      txnType:          { enum: ["DEPOSIT", "WITHDRAW", "TRANSFER_IN", "TRANSFER_OUT"] },
      amount:           { bsonType: "decimal", minimum: 0, exclusiveMinimum: true },  // amount > 0
      relatedAccountId: { bsonType: "long" },
      createdAt:        { bsonType: "date" }
    },
    // Transfers must name the other account; deposits and withdrawals must not
    anyOf: [
      { properties: { txnType: { enum: ["TRANSFER_IN", "TRANSFER_OUT"] } }, required: ["relatedAccountId"] },
      { properties: { txnType: { enum: ["DEPOSIT", "WITHDRAW"] } }, not: { required: ["relatedAccountId"] } }
    ]
  },

  // Append-only record of every change and every rejected or failed attempt:
  // who, when, which accounts, how much, and why. The app only ever adds
  // audit events; it has no code to change or delete them.
  audit_log: {
    bsonType: "object",
    required: ["_id", "referenceId", "timestamp", "actor", "action", "outcome"],
    properties: {
      _id:              { bsonType: "long" },
      referenceId:      { bsonType: "string", minLength: 1 },           // unique trace ID (UUID)
      timestamp:        { bsonType: "date" },                           // UTC
      actor:            { bsonType: "string", minLength: 1 },           // who
      action:           { enum: ["USER_CREATED", "USER_UPDATED", "USER_DELETED",
                                 "ACCOUNT_CREATED", "ACCOUNT_UPDATED", "ACCOUNT_DELETED",
                                 "ACCOUNT_FROZEN", "ACCOUNT_UNFROZEN",
                                 "DEPOSIT", "WITHDRAW", "TRANSFER",
                                 "TRANSFER_SCHEDULED", "SCHEDULED_TRANSFER_CANCELLED",
                                 "LOGIN", "ACCESS_DENIED"] },
      outcome:          { enum: ["SUCCESS", "REJECTED", "FAILED"] },
      reason:           { bsonType: "string" },
      userId:           { bsonType: "long" },
      accountId:        { bsonType: "long" },
      relatedAccountId: { bsonType: "long" },
      amount:           { bsonType: "decimal" },
      transactionIds:   { bsonType: "array", items: { bsonType: "long" } },
      details:          { bsonType: "string" }
    },
    // Anything that isn't a success must say why
    anyOf: [
      { properties: { outcome: { enum: ["SUCCESS"] } } },
      { required: ["reason"] }
    ]
  },

  // Transfers to run at a future date and time. Anything FAILED must say why.
  scheduled_transfers: {
    bsonType: "object",
    required: ["_id", "ownerUserId", "fromAccountId", "toAccountId", "amount", "scheduledFor", "status", "createdAt"],
    properties: {
      _id:           { bsonType: "long" },
      ownerUserId:   { bsonType: "long" },
      fromAccountId: { bsonType: "long" },
      toAccountId:   { bsonType: "long" },
      amount:        { bsonType: "decimal", minimum: 0, exclusiveMinimum: true },
      scheduledFor:  { bsonType: "date" },                                 // UTC
      status:        { enum: ["PENDING", "COMPLETED", "FAILED", "CANCELLED"] },
      createdAt:     { bsonType: "date" },
      processedAt:   { bsonType: "date" },
      failureReason: { bsonType: "string" }
    },
    anyOf: [
      { properties: { status: { enum: ["PENDING", "COMPLETED", "CANCELLED"] } } },
      { required: ["failureReason"] }
    ]
  }
};

for (const [name, schema] of Object.entries(rules)) {
  const options = { validator: { $jsonSchema: schema }, validationLevel: "strict", validationAction: "error" };
  if (db.getCollectionNames().includes(name)) {
    db.runCommand({ collMod: name, ...options });
  } else {
    db.createCollection(name, options);
  }
  print(`Validation rules applied to '${name}'`);
}

// Indexes (the app creates these too, from @Indexed and @CompoundIndex)
const indexes = [
  ["users",        { email: 1 },                               { name: "email", unique: true }],  // unique email
  ["users",        { "address.state": 1, "address.city": 1 },  { name: "address_location" }],     // search by location
  ["accounts",     { userId: 1 },                              { name: "userId" }],               // a user's accounts
  ["transactions", { accountId: 1, _id: -1 },                  { name: "account_history" }],      // history, newest first
  ["audit_log",    { referenceId: 1 },                         { name: "referenceId", unique: true }],
  ["audit_log",    { timestamp: 1 },                           { name: "timestamp" }],
  ["audit_log",    { transactionIds: 1 },                      { name: "transactionIds" }],       // trace a history record
  ["audit_log",    { accountId: 1, _id: -1 },                  { name: "account_events" }],
  ["audit_log",    { relatedAccountId: 1, _id: -1 },           { name: "related_account_events" }],
  ["audit_log",    { userId: 1, _id: -1 },                     { name: "user_events" }],
  ["scheduled_transfers", { status: 1, scheduledFor: 1 },      { name: "due" }],              // what's due now
  ["scheduled_transfers", { ownerUserId: 1, _id: -1 },         { name: "owner_newest" }]
];
for (const [collection, keys, options] of indexes) {
  try {
    db.getCollection(collection).createIndex(keys, options);
    print(`Index '${options.name}' ready on '${collection}'`);
  } catch (e) {
    print(`Index '${options.name}' on '${collection}': ${e.message}`);
  }
}
