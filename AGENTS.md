# AGENTS Guide

This document describes how contributors (human or AI agents) should work in this repository.

## Scope

- Backend API built with Spring Boot.
- Main modules: `auth`, `chat`, `user`.
- Shared cross-cutting module: `common`.

## Folder Management

- Keep modular hexagonal structure under `src/main/java/com/legalfam/backend`.
- For each business module (`auth`, `chat`, `user`), use:
  - `domain/**` for entities/domain models and domain exceptions.
  - `application/**` for use cases, ports, DTOs/events, and application services.
  - `infrastructure/**` for controllers, handlers, adapters, repository interfaces, integrations, and module-level config.
- Place cross-domain concerns in `common/**`:
  - `common/config/**` for shared configuration.
  - `common/error/**` for shared API error models/factories/exceptions/handlers.
- Mirror main packages in `src/test/java/com/legalfam/backend/**`.
- Do not place business/domain logic in `common/config/**`.
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
- `POST /api/v1/chat/sessions`
- `GET /api/v1/chat/subscribe/{sessionId}`
- `POST /api/v1/chat/send`
- `GET /api/v1/chat/sessions`
- `GET /api/v1/chat/sessions/{sessionId}/messages`
- `PATCH /api/v1/chat/messages/{messageId}/rating`

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
- `chat_sessions`
- `chat_messages`
- `chat_citations`

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
2. Add/update `application` ports if new use cases/integrations are needed.
3. Add/update infrastructure adapters for outbound ports.
4. Add or update a domain-scoped exception handler (`@RestControllerAdvice(basePackages = "<domain-package>")`) for domain-specific errors (same pattern as `auth` and `chat`).
5. Update security rules in `SecurityConfig`.
6. For chat asynchronous flows, update RabbitMQ topology/properties/listeners if message contracts change.
7. Update Swagger annotations (requests/responses/security requirement).
8. Ensure protected routes require Bearer token.
9. Update `README.md` with new endpoint usage.

## Local Run

```bash
./mvnw spring-boot:run
```

App default URL: `http://localhost:8080`
