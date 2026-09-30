# Simple Bank Application

A full-stack banking app where users can create accounts, view account details,
deposit, withdraw, and transfer money, and view paginated transaction history.
Users and accounts support full CRUD (create, read, update, delete), one user can
have many accounts, users and accounts can be searched by address and balance, and
every change and every rejected attempt is recorded in an append-only audit trail. Customers
register and log in with JWT tokens, and can only reach their own profile and accounts; bank
staff (admins) can reach everything.

Full specification: [docs/Simple_Bank_Application_Project_Document.pdf](docs/Simple_Bank_Application_Project_Document.pdf)

**Stack:** Spring Boot 4 (Java 21) · Spring Data MongoDB · MongoDB Atlas · Spring Security (JWT) · React · AWS

## Roadmap

Each step has its own branch, and each branch builds on the one before it.
`main` holds only the initial project skeleton.

| Step | Branch | Status |
|------|--------|--------|
| 1. Backend without database | `step-1-backend-no-db` | Done |
| 2. Backend with a database (MongoDB Atlas) | `step-2-backend-db` | Done |
| 3. JWT authentication | `step-3-backend-jwt` | Done |
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

    Security filters  →  Controller  →  Service  →  Repository  →  MongoDB Atlas
    (JWT check,          (HTTP, and     (business     (Spring Data
     role rules)          "is it         rules,        MongoDB)
                          yours?")       transactions,
                                         audit)

- **Security filters** (Spring Security) check the login token on every request and apply the
  role rules, e.g. only admins can list all users or read the audit log.
- **Controllers** handle HTTP, and ask `AccessGuard` whether the profile or account belongs to
  the caller before calling a service.
- **Services** hold all business rules. Every operation that changes an account runs in a
  MongoDB multi-document transaction, so its changes are saved together or not at all.
- **Repositories** are Spring Data `MongoRepository` interfaces. Spring builds the
  queries from the method names (derived queries).
- **Documents** (`User`, `Account`, `Transaction`, `AuditEvent`) map to the `users`, `accounts`,
  `transactions`, and `audit_log` collections.
- **Audit trail:** `AuditController` → `AuditService` → `AuditRepository` → `audit_log`.

In Step 1 the repositories stored data in memory. Because the services only depended on
repository interfaces, switching to a database did not change the controllers or business rules.

## Data model

Example documents (MongoDB stores `_id` for the ID, and money as exact `Decimal128`):

    users:         { _id: 1, name: "John Doe", email: "john@example.com",
                     address: { street: "100 Main St", city: "Baltimore", state: "MD", zip: "21201" },
                     passwordHash: "$2a$10$...", role: "CUSTOMER", createdAt: ISODate(...) }
    accounts:      { _id: 1, userId: 1, balance: NumberDecimal("300.00"), accountType: "SAVINGS", createdAt: ... }
    transactions:  { _id: 2, accountId: 1, txnType: "TRANSFER_OUT", amount: NumberDecimal("200.00"),
                     relatedAccountId: 2, createdAt: ... }
    audit_log:     { _id: 7, referenceId: "93197a42-...", timestamp: ISODate("2026-09-30T13:50:33Z"),
                     actor: "john@example.com (user 1, CUSTOMER)", action: "TRANSFER", outcome: "SUCCESS",
                     userId: 1, accountId: 1, relatedAccountId: 2, amount: NumberDecimal("200.00"),
                     transactionIds: [3, 4], details: "from balance 500.00 -> 300.00; to balance 0.00 -> 200.00" }
    counters:      { _id: "accounts", seq: 2 }

- **Numeric IDs.** MongoDB's default IDs are long codes. To keep the API's numeric IDs
  (1, 2, 3...), the `counters` collection holds the next number for each collection, and a
  save callback (`SequenceIdAssigner`) assigns it with an atomic `$inc`, like `AUTO_INCREMENT`.
- **Relationships.** `accounts.userId` and `transactions.accountId` reference other documents.
  MongoDB has no foreign keys, so the services check that owners exist.
- **Addresses** are embedded documents inside each user, since an address always belongs to
  exactly one user. Users created before addresses were added have none (`"address": null`).

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

### 2. Set the environment variables

Four settings come from environment variables, so no secret is ever committed. Add these lines
to `~/.bashrc` to set them for every terminal:

    export MONGODB_URI="mongodb+srv://bankapp:<password>@<cluster-address>/simple_bank?retryWrites=true&w=majority"
    export JWT_SECRET="<at least 32 random characters>"      # e.g. from: openssl rand -base64 48
    export ADMIN_EMAIL="admin@simplebank.com"
    export ADMIN_PASSWORD="<a strong password>"

- `JWT_SECRET` signs the login tokens (HS256). Anyone with it could forge tokens. The app
  refuses to start if it is missing or shorter than 32 characters.
- `ADMIN_EMAIL` and `ADMIN_PASSWORD` create the first bank staff (admin) account at startup,
  if it doesn't exist yet. Customers can't make themselves admins.

### 3. Run

    cd backend
    ./mvnw spring-boot:run     # starts on http://localhost:8080

At startup the app pings MongoDB (failing fast with a clear error if it can't connect),
creates the indexes, applies the validation rules to each collection, and creates the admin
account if needed.

Swagger UI: http://localhost:8080/swagger-ui.html. Log in with `POST /api/auth/login`, click
**Authorize**, and paste the `accessToken`; every request from Swagger UI then sends it.

### Setting up a database by hand (optional)

`database/mongo-setup.js` creates the same collections, validation rules, and indexes
the app applies at startup. It can be run with mongosh:

    mongosh "$MONGODB_URI" database/mongo-setup.js

## Rules enforced by MongoDB itself

Each collection has a **JSON Schema validator** (the MongoDB equivalent of SQL `CHECK`
constraints). MongoDB rejects any insert or update that breaks them, even ones that bypass the app:

| Collection | Rules |
|---|---|
| `users` | name, email, address, and role required; email format; **unique email** (index); address has street, city, a 2-letter uppercase state, and a 5-digit or ZIP+4 zip; role is `CUSTOMER` or `ADMIN`; a stored password must be a hash |
| `accounts` | balance is a Decimal128 **≥ 0**; type is `SAVINGS` or `CHECKING`; owner required |
| `transactions` | amount is a Decimal128 **> 0**; valid type; transfers **must** name the other account, deposits and withdrawals **must not** |
| `audit_log` | reference ID, UTC timestamp, actor, action, and outcome required; valid action and outcome; anything that isn't a success **must** include a reason |

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
- **Security tests** send requests through the real Spring Security filters: missing, tampered,
  expired, and forged tokens get 401; customers reaching someone else's data or staff endpoints
  get 403; and logins and access denials appear in the audit log.

Tests use a fixed test-only signing key (in `src/test/resources/application.properties`), so
they don't need `JWT_SECRET`.

## Security (JWT)

Customers register and log in; every other request sends the login token:

    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

A token is a signed **JWT** (HS256) holding the user's ID, email, and role, and it expires after
**1 hour**. The server checks the signature on every request, so a token can't be altered or
forged without the secret key, and no sessions are stored on the server (**stateless**).
Passwords are stored only as **BCrypt** hashes.

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| POST | `/api/auth/register` | `{ "name": "...", "email": "...", "password": "...", "address": { ... } }` | 201 |
| POST | `/api/auth/login` | `{ "email": "...", "password": "..." }` | 200 |
| GET | `/api/auth/me` | | 200 |

Register and login return:

    {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
      "tokenType": "Bearer",
      "expiresAt": "2026-09-30T15:32:46Z",
      "user": { "userId": 1, "name": "John Doe", "email": "john@example.com", "address": { ... }, "role": "CUSTOMER", ... }
    }

Passwords must be 8-72 characters with at least one letter and one number. Everyone who
registers is a `CUSTOMER`; a `role` sent in the request is ignored.

**Who can do what:**

| | Customer | Admin (bank staff) |
|---|---|---|
| Register, log in | Yes (no token needed) | Log in |
| Profile: read, update, delete | Own only | Anyone's |
| Open accounts | For themselves | For anyone |
| Accounts: read, retype, delete, deposit, withdraw, history | Own only | Anyone's |
| Transfers | **From** their own accounts, **to** any account | Any |
| List and search all users and accounts, premium accounts | No | Yes |
| Audit log | No | Yes |

- **401 Unauthorized:** no token, or the token is invalid, tampered with, or expired. Log in again.
- **403 Forbidden:** logged in, but it isn't yours, or the endpoint is for staff only.
- **Wrong email or password** always gives the same "Invalid email or password" (401), so the login
  form can't be used to find out which emails have accounts.
- **Every login** (successful or not) and **every denied attempt** (`ACCESS_DENIED`) is recorded in the
  audit trail, and the audit `actor` is the logged-in user, e.g. `john@example.com (user 1, CUSTOMER)`.

Try it with curl:

    TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
      -d '{"email":"john@example.com","password":"Secret123"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
    curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"

## API

All endpoints below need a login token. Endpoints marked **(admin)** are for bank staff only.

### Users

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| GET | `/api/users?page=0&size=20` (plus optional filters, below) **(admin)** | | 200 |
| GET | `/api/users/{id}` | | 200 |
| GET | `/api/users/{id}/accounts` | | 200 |
| PUT | `/api/users/{id}` | `{ "name": "John Smith", "email": "john@example.com", "address": { ... } }` | 200 |
| DELETE | `/api/users/{id}` | | 204 |

### Accounts

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| POST | `/api/accounts` | `{ "userId": 1, "accountType": "SAVINGS" }` | 201 |
| GET | `/api/accounts?page=0&size=20` (plus optional filters, below) **(admin)** | | 200 |
| GET | `/api/accounts/premium?threshold=1000` **(admin)** | | 200 |
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

New users are created by registering (`POST /api/auth/register`, above). The address is
required when registering or updating a user:

    "address": { "street": "100 Main St", "city": "Baltimore", "state": "MD", "zip": "21201" }

`state` is a 2-letter code (stored uppercase, so `md` becomes `MD`), and `zip` is 5 digits
or ZIP+4 (`21201-1234`). Errors inside the address are reported per field, e.g.
`{ "address.zip": "zip must be 5 digits or ZIP+4, e.g. 21201 or 21201-1234" }`.

`accountType` is `SAVINGS` or `CHECKING`. Deposit and withdraw return the updated
account; a transfer returns both updated accounts. `GET /api/users/{id}/accounts`
returns a plain list; the other lists are paginated.

### Search and filters

All filters are optional, can be combined, and work together with `page` and `size`.

**Users** (`GET /api/users`):

| Parameter | Meaning |
|---|---|
| `state` | 2-letter state code, case-insensitive (`md` finds `MD`) |
| `city` | Exact city name, case-insensitive |
| `zip` | Exact ZIP code |
| `minBalance` | At least this much money (inclusive) |
| `maxBalance` | At most this much money (inclusive) |
| `balanceMode` | `TOTAL` (default): the sum of all the user's accounts. `ANY_ACCOUNT`: at least one single account in the range |

    GET /api/users?state=MD&city=Baltimore
    GET /api/users?zip=21201&minBalance=100
    GET /api/users?minBalance=100&maxBalance=500&balanceMode=ANY_ACCOUNT

With `TOTAL`, a user with $250 and $20 in two accounts counts as $270; with `ANY_ACCOUNT`,
each account is checked on its own. Balance filters use a MongoDB **aggregation pipeline**
that joins each user's accounts (`$lookup`) and, for `TOTAL`, adds them up (`$sum`).

**Accounts** (`GET /api/accounts`):

| Parameter | Meaning |
|---|---|
| `minBalance` | Balance of at least this much (inclusive) |
| `maxBalance` | Balance of at most this much (inclusive) |
| `accountType` | `SAVINGS` or `CHECKING` |

    GET /api/accounts?minBalance=100&accountType=SAVINGS

`minBalance` above `maxBalance`, an unknown `balanceMode`, or an unknown `accountType` returns a 400.

### Premium accounts

`GET /api/accounts/premium?threshold=1000` returns every account whose balance is **at or above**
the threshold, **richest first** (equal balances oldest first), paginated. `threshold` is required
and must be greater than zero.

## Audit trail

Every change and every rejected or failed attempt is recorded in the `audit_log` collection, for
fraud and loss prevention and compliance: **who, when, which accounts, how much, and why**.
Logins and denied access attempts are recorded too. The audit endpoints are for **admins** only.

| Field | Meaning |
|---|---|
| `auditId`, `referenceId` | A number and a unique trace ID (UUID) |
| `timestamp` | When it happened, in UTC (e.g. `2026-09-30T13:50:33.259Z`) |
| `actor` | Who did it: the logged-in user, e.g. `john@example.com (user 1, CUSTOMER)`. Before logging in (registering, logging in): `anonymous@<caller IP address>` |
| `action` | `USER_CREATED`, `USER_UPDATED`, `USER_DELETED`, `ACCOUNT_CREATED`, `ACCOUNT_UPDATED`, `ACCOUNT_DELETED`, `DEPOSIT`, `WITHDRAW`, `TRANSFER`, `LOGIN`, `ACCESS_DENIED` |
| `outcome` | `SUCCESS`, `REJECTED` (a business rule refused it), or `FAILED` (a system problem) |
| `reason` | Why it was rejected or failed |
| `userId`, `accountId`, `relatedAccountId` | The user and accounts involved (a transfer has both accounts) |
| `amount` | The amount requested, recorded even when rejected |
| `transactionIds` | The history records created (a transfer creates two, linked to one event) |
| `details` | Context such as the balance before and after, or which user fields changed |

**How it stays trustworthy:**

- **Successes are recorded in the same MongoDB transaction** as the change itself, so money never
  moves without an audit record.
- **Rejected and failed attempts are recorded after the transaction rolls back**, so they are on
  record even though nothing changed.
- **Append-only:** the audit repository has no update or delete methods, and the API has no
  endpoints to create, change, or delete audit events. Deleting an account or user does not erase
  its audit trail.

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/audit?accountId=&userId=&action=&outcome=&from=&to=&page=0&size=20` | Search, newest first. `accountId` matches either side of a transfer; `from` and `to` are UTC timestamps |
| GET | `/api/audit/{auditId}` | One event |
| GET | `/api/audit/reference/{referenceId}` | One event by its trace ID |
| GET | `/api/audit/transactions/{txnId}` | Trace a history record back to the event that created it |

    GET /api/audit?accountId=1&outcome=REJECTED
    GET /api/audit?action=TRANSFER&from=2026-09-30T00:00:00Z

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
| 400 | Validation failure (including the address and password rules), invalid amount, insufficient funds, transfer to the same account, invalid page, size, or search filter, missing or invalid premium threshold, `from` after `to` in an audit search, malformed JSON |
| 401 | Not logged in; the token is invalid, tampered with, or expired; or wrong email or password at login |
| 403 | Logged in, but the profile or account isn't yours, or the endpoint is for staff (admin) only |
| 404 | User, account, or audit event not found |
| 409 | Email already registered, deleting an account that has money, deleting a user who has accounts, or a database rule violation |
| 415 | Request body sent without `Content-Type: application/json` |
| 503 | The account stayed busy with other requests; safe to retry |

## CORS

The API accepts browser requests from `http://localhost:5173` (Vite) and
`http://localhost:3000` (Create React App) only. See `config/CorsConfig.java`;
the deployed frontend's URL is added there in Step 5.

## Testing with Postman

Import `postman/Joseph_Hilte_simplebank.json` and start the backend. Then open the collection's
**Variables** tab and set the **Current value** of `adminEmail` and `adminPassword` to the
`ADMIN_EMAIL` and `ADMIN_PASSWORD` the backend was started with. (Leave the Initial value empty,
since it is exported with the collection.) Then run the whole collection:

- **Log in (admin)** gets the bank staff token used by admin-only requests.

- **Happy path** registers a customer and logs in, then reads and updates the profile, creates two accounts, deposits,
  withdraws, transfers between the accounts, pages through lists and history, and
  searches users (by city, state, and both balance modes) and accounts (by balance and type),
  lists premium accounts, and traces the transfer through the audit log.
- **Business rules and errors** checks that every rule returns the correct status code, and that
  rejected attempts are recorded in the audit log.
- **Security (401 and 403)** checks missing and tampered tokens, wrong passwords, that customers
  can't use staff endpoints or reach another customer's profile and accounts, that registering
  can't make you an admin, and that denied attempts and failed logins are audited.
- **Cleanup (delete)** empties and deletes the accounts and users that were created, and checks
  that the deletions were audited.

Requests send the customer's token by default (Bearer auth set on the collection); admin-only
requests use the admin's token, and register and login send none.

It can also be run from the command line with Newman, Postman's command-line runner, which saves
the results as a report:

    npx -y -p newman -p newman-reporter-htmlextra newman run postman/Joseph_Hilte_simplebank.json \
      --env-var "adminEmail=$ADMIN_EMAIL" --env-var "adminPassword=$ADMIN_PASSWORD" \
      --reporters cli,htmlextra --reporter-htmlextra-export postman/Joseph_Hilte_simplebank_report.html

The collection generates a new email, city, and password on every run, so it can be run
repeatedly against the same database, and the search checks only see that run's users.
