# Simple Bank Application

A full-stack banking app where users can create accounts, view account details,
deposit, withdraw, and transfer money, and view paginated transaction history.
Users and accounts support full CRUD (create, read, update, delete), and one user
can have many accounts.

Full specification: [docs/Simple_Bank_Application_Project_Document.pdf](docs/Simple_Bank_Application_Project_Document.pdf)

**Stack:** Spring Boot 4 (Java 21) · Spring Data JPA / Hibernate · MySQL 8.4 · React · AWS

## Roadmap

Each step has its own branch, and each branch builds on the one before it.
`main` holds only the initial project skeleton.

| Step | Branch | Status |
|------|--------|--------|
| 1. Backend without database | `step-1-backend-no-db` | Done |
| 2. Backend with MySQL | `step-2-backend-db` | Done |
| 3. JWT authentication (optional) | `step-3-backend-jwt` | Not started |
| 4. React frontend | `step-4-react-frontend` | Not started |
| 5. Deploy to AWS | `step-5-aws-deploy` | Not started |

## Repository layout

    backend/     Spring Boot REST API
    frontend/    React app (Step 4)
    database/    SQL schema script
    postman/     Postman collection
    docs/        Project document and UI screenshots

## Backend architecture

    Controller  →  Service  →  Repository  →  MySQL
    (HTTP only)    (business     (Spring Data JPA)
                    rules,
                    transactions)

- **Controllers** only handle HTTP: read the request, call a service, return the result.
- **Services** hold all business rules. Every public method runs in a database
  transaction (`@Transactional`), so its changes are saved together or not at all.
- **Repositories** are Spring Data JPA interfaces. Spring generates the SQL from the
  method names (derived queries).
- **Entities** (`User`, `Account`, `Transaction`) map to the tables in `database/schema.sql`.

In Step 1 the repositories stored data in memory. Because the services only depended on
repository interfaces, switching to MySQL did not change the controllers or business rules.

## Setup

Requirements: **Java 21** and **MySQL 8.4**. Maven is not needed; the included wrapper
(`mvnw`) downloads it.

### 1. Create the database and app user (once, as root)

    mysql -u root -p

```sql
CREATE DATABASE simple_bank;
CREATE USER 'bankapp'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON simple_bank.* TO 'bankapp'@'localhost';
```

The app uses its own `bankapp` user with access to `simple_bank` only, not `root`.

### 2. Create the tables

From the repository root:

    mysql -u bankapp -p simple_bank

```sql
SOURCE database/schema.sql;
```

The script drops and recreates the tables, so rerunning it deletes all data.
Besides the tables from the specification, it adds constraints the database enforces
on its own: `NOT NULL`, a unique email, foreign keys, and `CHECK` rules
(balance ≥ 0, amount > 0, valid account and transaction types, and transfers must
name the other account). It also adds `related_account_id` to `transactions` for
transfers.

### 3. Set the database password

The password is read from an environment variable so it is never committed:

    export DB_PASSWORD=choose-a-password

(Add that line to `~/.bashrc` to set it for every terminal.) The username defaults to
`bankapp`; override it with `DB_USERNAME` if needed.

### 4. Run

    cd backend
    ./mvnw spring-boot:run     # starts on http://localhost:8080

At startup Hibernate checks that the entities match the tables (`ddl-auto=validate`)
and refuses to start if they don't.

Swagger UI: http://localhost:8080/swagger-ui.html

## Tests

    cd backend
    ./mvnw test

Tests run against **H2**, an in-memory database, using `src/test/resources/application.properties`.
They don't need MySQL or `DB_PASSWORD`. Each test runs in a transaction that is rolled
back afterwards, so every test starts with an empty database.

## API

### Users

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| POST | `/api/users` | `{ "name": "John Doe", "email": "john@example.com" }` | 201 |
| GET | `/api/users` | | 200 |
| GET | `/api/users/{id}` | | 200 |
| GET | `/api/users/{id}/accounts` | | 200 |
| PUT | `/api/users/{id}` | `{ "name": "John Smith", "email": "john@example.com" }` | 200 |
| DELETE | `/api/users/{id}` | | 204 |

### Accounts

| Method | Endpoint | Body | Success |
|--------|----------|------|---------|
| POST | `/api/accounts` | `{ "userId": 1, "accountType": "SAVINGS" }` | 201 |
| GET | `/api/accounts` | | 200 |
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
account; a transfer returns both updated accounts. Lists are returned in ID order.

### Transaction history (paginated)

History is returned newest first, one page at a time. `page` starts at 0 (default 0),
and `size` is 1–100 (default 20):

    {
      "content": [
        { "txnId": 4, "type": "TRANSFER_OUT", "amount": 100.00, "relatedAccountId": 2, "date": "2026-09-29T12:01:22.175016" },
        { "txnId": 2, "type": "WITHDRAW", "amount": 200.00, "relatedAccountId": null, "date": "2026-09-29T11:59:10.126873" }
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

- Deposit and withdrawal amounts must be greater than zero, with at most 2 decimal places.
- A withdrawal cannot exceed the current balance.
- Every successful deposit and withdrawal is recorded as a transaction. Failed ones change nothing.
- Emails are unique (case-insensitive), including when a user is updated.
- An account's balance cannot be edited directly; only its type can be updated.
  Money only moves through deposits and withdrawals.
- An account can only be deleted when its balance is 0.00. Its transactions are deleted with it.
- A user can only be deleted when they have no accounts.
- A transfer needs enough money in the source account, and can't go to the same account.
  It records `TRANSFER_OUT` on the source and `TRANSFER_IN` on the destination, and
  either both balances change or neither does.
- Transfer history is kept even if the other account is later deleted
  (`related_account_id` deliberately has no foreign key).

### Concurrency

Deposits, withdrawals, transfers, and account deletion lock the account's row
(`SELECT ... FOR UPDATE`) until their transaction commits. Two requests on the same
account take turns, so two simultaneous withdrawals can't both spend the same money.
Requests on different accounts are not blocked.

A transfer locks both accounts, always the lower account ID first. Two transfers
between the same accounts in opposite directions therefore lock in the same order
and can't deadlock.

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
| 409 | Email already registered, deleting an account that has money, deleting a user who has accounts, or a database constraint violation |
| 415 | Request body sent without `Content-Type: application/json` |

## CORS

The API accepts browser requests from `http://localhost:5173` (Vite) and
`http://localhost:3000` (Create React App) only. See `config/CorsConfig.java`;
the deployed frontend's URL is added there in Step 5.

## Testing with Postman

Import `postman/SimpleBank.postman_collection.json`, start the backend, and run the
whole collection:

- **Happy path** creates, reads, and updates a user and two accounts, deposits,
  withdraws, transfers between the accounts, and pages through the history.
- **Business rules and errors** checks that every rule returns the correct status code.
- **Cleanup (delete)** empties and deletes the accounts and users that were created.

The collection generates a new email on every run, so it can be run repeatedly
against the same database.
