# AGENTS Guide

This document describes how contributors (human or AI agents) should work in this repository.

## Scope

- Backend API built with Spring Boot.
- Primary domain currently includes authentication and users listing.

## Folder Management

- Keep package-by-domain structure under `src/main/java/com/legalfam/backend`.
- Place auth domain files in `auth/**`.
  - Exceptions: `auth/exception/**`
  - Exception handlers: `auth/exception/handler/**`
  - Controllers/services/DTOs/tokens in their existing auth subpackages.
- Place user domain files in `user/**` (controllers, repositories, DTOs).
- Place chat domain files in `chat/**`.
  - Exceptions: `chat/exception/**`
  - Exception handlers: `chat/exception/handler/**`
- Place cross-domain concerns in dedicated packages:
  - `config/**` for security/filter/config beans.
  - `error/**` for shared API error models and shared/common errors.
    - Shared exceptions: `error/exception/**`
    - Shared/global handlers: `error/handler/**`
- Mirror main packages in `src/test/java/com/legalfam/backend/**`.
- Do not place business/domain logic in `config/**`.
- Add new packages only when an existing domain/shared package is clearly not appropriate.

## Core Rules

- Do not expose or return password hashes in API responses.
- Use `/api/v1` as the API prefix for all endpoints.
- Keep `/api/v1/auth/**` public.
- Keep non-auth endpoints protected with JWT Bearer authentication unless explicitly requested otherwise.
- Preserve refresh token rotation behavior.
- Preserve `401` for unauthenticated requests and `403` for forbidden requests.
- Do not run compile/build verification commands after making changes unless explicitly requested by the user.

## Current API Surface

### Public

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`

### Protected

- `GET /api/v1/users`

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
2. Add or update a domain-scoped exception handler (`@RestControllerAdvice(basePackages = "<domain-package>")`) for domain-specific errors (same pattern as `auth` and `chat`).
3. Update security rules in `SecurityConfig`.
4. Update Swagger annotations (requests/responses/security requirement).
5. Ensure protected routes require Bearer token.
6. Update `README.md` with new endpoint usage.

## Local Run

```bash
./mvnw spring-boot:run
```

App default URL: `http://localhost:8080`
