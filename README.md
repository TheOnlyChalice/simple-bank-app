# Simple Bank Application

A full-stack banking app where users can create accounts, view account details,
deposit and withdraw money, and view transaction history. Users and accounts
support full CRUD (create, read, update, delete), and one user can have many accounts.

Full specification: [docs/Simple_Bank_Application_Project_Document.pdf](docs/Simple_Bank_Application_Project_Document.pdf)

**Stack:** Spring Boot 4 (Java 21) · MySQL · React · AWS

## Roadmap

Each step has its own branch, and each branch builds on the one before it.
`main` holds only the initial project skeleton.

| Step | Branch | Status |
|------|--------|--------|
| 1. Backend without database | `step-1-backend-no-db` | Done |
| 2. Backend with MySQL | `step-2-backend-db` | Not started |
| 3. JWT authentication (optional) | `step-3-backend-jwt` | Not started |
| 4. React frontend | `step-4-react-frontend` | Not started |
| 5. Deploy to AWS | `step-5-aws-deploy` | Not started |

## Repository layout

    backend/     Spring Boot REST API
    frontend/    React app (Step 4)
    database/    SQL scripts (Step 2)
    postman/     Postman collection
    docs/        Project document and UI screenshots

## Backend architecture

    Controller  →  Service  →  Repository (interface)  →  Storage
    (HTTP only)    (business                               Step 1: in-memory maps
                    rules)                                 Step 2: MySQL

Controllers only handle HTTP. All business rules live in the service layer.
Services depend on repository interfaces, so switching from in-memory storage
to MySQL in Step 2 doesn't change the service or controller code.

## Running the backend

Requirements: Java 21. Maven is not needed; the included wrapper (`mvnw`) downloads it.

    cd backend
    ./mvnw spring-boot:run     # starts on http://localhost:8080
    ./mvnw test                # runs the unit tests

Swagger UI: http://localhost:8080/swagger-ui.html

> Step 1 stores data in memory, so everything resets when the app restarts.

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
| GET | `/api/accounts/{id}/transactions` | | 200 |

`accountType` is `SAVINGS` or `CHECKING`. Deposit and withdraw return the updated
account. Transactions are returned newest first. Lists are returned in ID order.

## Business rules

- Deposit and withdrawal amounts must be greater than zero, with at most 2 decimal places.
- A withdrawal cannot exceed the current balance.
- Every successful deposit and withdrawal is recorded as a transaction. Failed ones change nothing.
- Emails are unique (case-insensitive), including when a user is updated.
- An account's balance cannot be edited directly; only its type can be updated.
  Money only moves through deposits and withdrawals.
- An account can only be deleted when its balance is 0.00. Its transactions are deleted with it.
- A user can only be deleted when they have no accounts.

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
| 400 | Validation failure, invalid amount, insufficient funds, malformed JSON |
| 404 | User or account not found |
| 409 | Email already registered, deleting an account that has money, deleting a user who has accounts |

## Testing with Postman

Import `postman/SimpleBank.postman_collection.json`, start the backend, and run the
whole collection:

- **Happy path** creates, reads, and updates a user and two accounts, and saves their IDs.
- **Business rules and errors** checks that every rule returns the correct status code.
- **Cleanup (delete)** deletes the accounts and users that were created.
