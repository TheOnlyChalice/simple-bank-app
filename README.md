# Simple Bank Application

A full-stack banking app where users can create an account, view account details,
deposit and withdraw money, and view transaction history.

Full specification: [docs/Simple_Bank_Application_Project_Document.pdf](docs/Simple_Bank_Application_Project_Document.pdf)

**Stack:** Spring Boot (Java 21) · MySQL · React · AWS

## Roadmap

Each step is built on its own branch and merged into `main` through a pull request when finished.

| Step | Branch | Status |
|------|--------|--------|
| 1. Backend without database | `step-1-backend-no-db` | In progress |
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
