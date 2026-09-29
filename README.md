# Simple Bank Application

A full-stack banking app where users can create accounts, view account details,
deposit, withdraw, and transfer money, and view paginated transaction history.
Users and accounts support full CRUD (create, read, update, delete), and one user
can have many accounts.

Full specification: [docs/Simple_Bank_Application_Project_Document.pdf](docs/Simple_Bank_Application_Project_Document.pdf)

**Stack:** Spring Boot 4 (Java 21) · Spring Data MongoDB · MongoDB Atlas · React · AWS

## Roadmap

Each step has its own branch, and each branch builds on the one before it.
`main` holds only the initial project skeleton.

| Step | Branch | Status |
|------|--------|--------|
| 1. Backend without database | `step-1-backend-no-db` | Done |
| 2. Backend with a database (MongoDB Atlas) | `step-2-backend-db` | Done |
| 3. JWT authentication (optional) | `step-3-backend-jwt` | Not started |
| 4. React frontend | `step-4-react-frontend` | Not started |
| 5. Deploy to AWS | `step-5-aws-deploy` | Not started |

Step 2 was first built on MySQL and then moved to MongoDB Atlas. The MySQL version
is still in this branch's Git history.

## Repository layout

    backend/     Spring Boot REST API
    frontend/    React app (Step 4)
    database/    MongoDB setup script (collections, validation rules, indexes)
    postman/     Postman collection
    docs/        Project document and UI screenshots

## Backend architecture

    Controller  →  Service  →  Repository  →  MongoDB Atlas
    (HTTP only)    (business     (Spring Data
                    rules,        MongoDB)
                    transactions)

- **Controllers** only handle HTTP: read the request, call a service, return the result.
- **Services** hold all business rules. Every operation that changes an account runs in a
  MongoDB multi-document transaction, so its changes are saved together or not at all.
- **Repositories** are Spring Data `MongoRepository` interfaces. Spring builds the
  queries from the method names (derived queries).
- **Documents** (`User`, `Account`, `Transaction`) map to the `users`, `accounts`, and
  `transactions` collections.

In Step 1 the repositories stored data in memory. Because the services only depended on
repository interfaces, switching to a database did not change the controllers or business rules.

## Data model

Example documents (MongoDB stores `_id` for the ID, and money as exact `Decimal128`):

    users:         { _id: 1, name: "John Doe", email: "john@example.com", createdAt: ISODate(...) }
    accounts:      { _id: 1, userId: 1, balance: NumberDecimal("300.00"), accountType: "SAVINGS", createdAt: ... }
    transactions:  { _id: 2, accountId: 1, txnType: "TRANSFER_OUT", amount: NumberDecimal("200.00"),
                     relatedAccountId: 2, createdAt: ... }
    counters:      { _id: "accounts", seq: 2 }

- **Numeric IDs.** MongoDB's default IDs are long codes. To keep the API's numeric IDs
  (1, 2, 3...), the `counters` collection holds the next number for each collection, and a
  save callback (`SequenceIdAssigner`) assigns it with an atomic `$inc`, like `AUTO_INCREMENT`.
- **Relationships.** `accounts.userId` and `transactions.accountId` reference other documents.
  MongoDB has no foreign keys, so the services check that owners exist.

## Setup

Requirements: **Java 21**. Maven is not needed; the included wrapper (`mvnw`) downloads it.
**Docker Desktop** is required to run the tests.

### 1. Create a MongoDB Atlas cluster

1. Create a free **M0** cluster at mongodb.com/cloud/atlas (AWS, us-east-1).
2. **Database Access:** create a user (e.g. `bankapp`) with a letters-and-numbers password and
   the role **Read and write to any database**, plus the specific privilege **`dbAdmin`** on the
   `simple_bank` database (needed to apply the validation rules).
3. **Network Access:** allow your IP address (or `0.0.0.0/0` for development).
4. **Connect → Drivers → Java:** copy the connection string.

### 2. Set the connection string

Insert the password and the database name `simple_bank`, and set it as an environment
variable so it is never committed:

    export MONGODB_URI="mongodb+srv://bankapp:<password>@<cluster-address>/simple_bank?retryWrites=true&w=majority"

(Add that line to `~/.bashrc` to set it for every terminal.)

### 3. Run

    cd backend
    ./mvnw spring-boot:run     # starts on http://localhost:8080

At startup the app pings MongoDB (failing fast with a clear error if it can't connect),
creates the indexes, and applies the validation rules to each collection.

Swagger UI: http://localhost:8080/swagger-ui.html

### Setting up a database by hand (optional)

`database/mongo-setup.js` creates the same collections, validation rules, and indexes
the app applies at startup. It can be run with mongosh:

    mongosh "$MONGODB_URI" database/mongo-setup.js

## Rules enforced by MongoDB itself

Each collection has a **JSON Schema validator** (the MongoDB equivalent of SQL `CHECK`
constraints). MongoDB rejects any insert or update that breaks them, even ones that bypass the app:

| Collection | Rules |
|---|---|
| `users` | name and email required; email format; max 100 characters; **unique email** (index) |
| `accounts` | balance is a Decimal128 **≥ 0**; type is `SAVINGS` or `CHECKING`; owner required |
| `transactions` | amount is a Decimal128 **> 0**; valid type; transfers **must** name the other account, deposits and withdrawals **must not** |

## Tests

    cd backend
    ./mvnw test

**Docker must be running.** Testcontainers starts a MongoDB 8.0 in Docker as a single-member
replica set (so transactions work), the app applies its rules and indexes to it, and the
container is removed afterwards. Tests never touch Atlas. The collections are emptied
before each test.

- **Service tests** check every business rule.
- **API tests** (MockMvc) check URLs, status codes, JSON, validation, the error format, and CORS.
- **MongoDB tests** check that the validation rules reject bad data written directly to MongoDB,
  and that simultaneous withdrawals, deposits, and transfers keep balances correct.

## API

### Users

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| POST | `/api/users` | `{ "name": "John Doe", "email": "john@example.com" }` | 201 |
| GET | `/api/users?page=0&size=20` | | 200 |
| GET | `/api/users/{id}` | | 200 |
| GET | `/api/users/{id}/accounts` | | 200 |
| PUT | `/api/users/{id}` | `{ "name": "John Smith", "email": "john@example.com" }` | 200 |
| DELETE | `/api/users/{id}` | | 204 |

### Accounts

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| POST | `/api/accounts` | `{ "userId": 1, "accountType": "SAVINGS" }` | 201 |
| GET | `/api/accounts?page=0&size=20` | | 200 |
| GET | `/api/accounts/{id}` | | 200 |
| PUT | `/api/accounts/{id}` | `{ "accountType": "CHECKING" }` | 200 |
| DELETE | `/api/accounts/{id}` | | 204 |
| POST | `/api/accounts/{id}/deposit` | `{ "amount": 500 }` | 200 |
| POST | `/api/accounts/{id}/withdraw` | `{ "amount": 200 }` | 200 |
| GET | `/api/accounts/{id}/transactions?page=0&size=20` | | 200 |

### Transfers

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| POST | `/api/transfers` | `{ "fromAccountId": 1, "toAccountId": 2, "amount": 100 }` | 200 |

`accountType` is `SAVINGS` or `CHECKING`. Deposit and withdraw return the updated
account; a transfer returns both updated accounts. `GET /api/users/{id}/accounts`
returns a plain list; the other lists are paginated.

### Pagination

The user list, account list, and transaction history are returned one page at a time.
`page` starts at 0 (default 0), and `size` is 1–100 (default 20). Users and accounts are
oldest first; transaction history is newest first:

    {
      "content": [
        { "txnId": 4, "type": "TRANSFER_OUT", "amount": 100.00, "relatedAccountId": 2, "date": "2026-09-29T12:01:22.175" },
        { "txnId": 2, "type": "WITHDRAW", "amount": 200.00, "relatedAccountId": null, "date": "2026-09-29T11:59:10.126" }
      ],
      "page": 0,
      "size": 2,
      "totalElements": 3,
      "totalPages": 2,
      "first": true,
      "last": false
    }

Transaction types are `DEPOSIT`, `WITHDRAW`, `TRANSFER_IN`, and `TRANSFER_OUT`.
For transfers, `relatedAccountId` is the other account; otherwise it is `null`.

## Business rules

- Deposit, withdrawal, and transfer amounts must be greater than zero, with at most 2 decimal places.
- A withdrawal or transfer cannot exceed the current balance.
- Every successful deposit, withdrawal, and transfer is recorded. Failed ones change nothing.
- Emails are unique (case-insensitive), including when a user is updated.
- An account's balance cannot be edited directly; only its type can be updated.
- An account can only be deleted when its balance is 0.00. Its transactions are deleted with it.
- A user can only be deleted when they have no accounts.
- A transfer can't go to the same account. It records `TRANSFER_OUT` on the source and
  `TRANSFER_IN` on the destination, and either both balances change or neither does.
  Transfer history is kept even if the other account is later deleted.

### Concurrency

Every operation that changes an account runs in a MongoDB transaction. If two requests change
the same account at the same moment, MongoDB aborts one with a write conflict
(`TransientTransactionError`) instead of letting it overwrite the other. The aborted
transaction is retried automatically with fresh data (exponential backoff with jitter, for up
to 20 seconds), so no update is ever lost and every rule is re-checked. Conflicting
transactions are aborted rather than made to wait, so deadlocks can't happen. If an account
stays too busy for the whole retry budget, the request returns **503** and nothing changes.

## Errors

Every error returns the same JSON shape:

    {
      "status": 409,
      "error": "Conflict",
      "message": "Account 1 has a balance of 300.00. Withdraw the full balance before deleting it.",
      "fieldErrors": {},
      "timestamp": "2026-09-29T10:17:11"
    }

`fieldErrors` lists problems per field for validation failures, e.g.
`{ "email": "Email must be a valid email address" }`.

| Status | When |
|--------|------|
| 400 | Validation failure, invalid amount, insufficient funds, transfer to the same account, invalid page or size, malformed JSON |
| 404 | User or account not found |
| 409 | Email already registered, deleting an account that has money, deleting a user who has accounts, or a database rule violation |
| 415 | Request body sent without `Content-Type: application/json` |
| 503 | The account stayed busy with other requests; safe to retry |

## CORS

The API accepts browser requests from `http://localhost:5173` (Vite) and
`http://localhost:3000` (Create React App) only. See `config/CorsConfig.java`;
the deployed frontend's URL is added there in Step 5.

## Testing with Postman

Import `postman/SimpleBank.postman_collection.json`, start the backend, and run the
whole collection:

- **Happy path** creates, reads, and updates a user and two accounts, deposits,
  withdraws, transfers between the accounts, and pages through lists and history.
- **Business rules and errors** checks that every rule returns the correct status code.
- **Cleanup (delete)** empties and deletes the accounts and users that were created.

The collection generates a new email on every run, so it can be run repeatedly
against the same database.
