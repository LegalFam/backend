# AGENTS Guide

This document describes how contributors (human or AI agents) should work in this repository.

## Scope

- Backend API built with Spring Boot.
- Primary domain currently includes authentication and users listing.

## Core Rules

- Do not expose or return password hashes in API responses.
- Keep `/api/auth/**` public.
- Keep non-auth endpoints protected with JWT Bearer authentication unless explicitly requested otherwise.
- Preserve refresh token rotation behavior.
- Preserve `401` for unauthenticated requests and `403` for forbidden requests.

## Current API Surface

### Public

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`

### Protected

- `GET /api/users`

## Security Expectations

- Password hashing: BCrypt.
- JWT secret must be set through `JWT_SECRET` and be at least 32 characters.
- Access token is short-lived; refresh token is persisted and revocable.
- Do not hardcode secrets in source code.

## Persistence

- Database: PostgreSQL.
- JPA entities auto-managed with `spring.jpa.hibernate.ddl-auto=update`.

Current relevant tables:
- `users`
- `refresh_tokens`

## Swagger / OpenAPI

- Swagger UI path: `/swagger-ui.html`
- API docs path: `/v3/api-docs`
- Protected endpoints should include OpenAPI security requirement (`bearerAuth`) so Swagger can send tokens.

## Implementation Conventions

- Prefer constructor injection.
- Keep controllers thin; business logic in services.
- Use DTOs for request/response payloads.
- Keep responses explicit with proper HTTP status codes.
- Avoid leaking internal exceptions/messages to clients.

## Change Checklist

When adding or changing endpoints:

1. Update controller + service + DTOs.
2. Update security rules in `SecurityConfig`.
3. Update Swagger annotations (requests/responses/security requirement).
4. Ensure protected routes require Bearer token.
5. Update `README.md` with new endpoint usage.

## Local Run

```bash
./mvnw spring-boot:run
```

App default URL: `http://localhost:8080`
