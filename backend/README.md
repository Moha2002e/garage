# Garage backend

REST API (Java 21, Spring Boot 3.3, PostgreSQL, Liquibase) for a garage: accounts, clients, vehicles, appointments, monthly statistics and Excel exports.

## Run locally

1. Copy `.env.example` to `.env` and fill it in (database password, `JWT_SECRET` of at least 32 characters, initial admin). `.env` is git-ignored.
2. Start PostgreSQL: `docker compose up -d`
3. Start Mailpit for the password-reset e-mails (SMTP 1025, UI http://localhost:8025), outside this compose file.
4. `mvn spring-boot:run`
5. API docs: http://localhost:8080/swagger-ui.html (disabled with the `prod` profile)

The first administrator is created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD`, only if no ADMIN exists.

## Security model

- JWT in an HttpOnly cookie (`SameSite=Lax`, `Secure` when `COOKIE_SECURE=true` / `prod` profile). Lifetime 8 h, or 30 days with "remember me".
- CSRF protection on, XSRF-TOKEN cookie read by Angular and sent as `X-XSRF-TOKEN`. Exempt: `login`, `register`, `forgot-password`, `reset-password` (no session to forge).
- Free sign-up creates a `CLIENT`; an admin promotes accounts. A `CLIENT` account sees nothing until an ADMIN/EMPLOYEE links it to a client record (`PUT /api/users/{id}/client`), then only that client's appointments (read-only).
- Password reset: token valid 30 minutes, single use, stored hashed; the answer is the same whether or not the e-mail exists.

| Role | Rights |
|---|---|
| ADMIN | everything, including deactivating clients, roles, Excel exports |
| EMPLOYEE | read/create/update clients, vehicles, appointments; statistics; link accounts; no deactivation, no export |
| CLIENT | read-only on own appointments |

## Personal data

The vehicle plate is personal data (GDPR): it is never logged or echoed in errors, and visible only to ADMIN/EMPLOYEE and the owning CLIENT. The appointments export includes plate and phone number (ADMIN only): the file is easy to share, so a retention policy and an erasure procedure are needed. Clients are deactivated, never physically deleted.

## Tests

`mvn test` — unit tests (Mockito) and integration tests using Testcontainers PostgreSQL (Docker must be running).
