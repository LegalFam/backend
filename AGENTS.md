# AGENTS Guide

This document describes how contributors (human or AI agents) should work in this repository.

## Scope

- Backend API built with Spring Boot.
- Main modules: `auth`, `chat`, `payment`, `user`.
- Shared cross-cutting module: `common`.

## Folder Management

- Keep modular hexagonal structure under `src/main/java/com/legalfam/backend`.
- For each business module (`auth`, `chat`, `payment`, `user`), use:
  - `domain/**` for entities/domain models and domain exceptions.
  - `application/**` for use cases, ports, DTOs/events, and application services.
  - `application/port/in/**` for inbound use case contracts.
  - `application/port/out/**` for outbound dependency contracts.
  - `infrastructure/**` for controllers, handlers, adapters, repository interfaces, integrations, and module-level config.
- Classify infrastructure adapters by hexagonal direction:
  - `infrastructure/adapter/in/**` for inbound adapters that trigger application behavior, such as controllers, message/event processors, scheduled workers, and listeners.
  - `infrastructure/adapter/out/**` for outbound adapters that implement application ports, such as persistence adapters, external API clients, event publishers, catalog adapters, and gateway adapters.
- Keep non-adapter infrastructure in explicit folders:
  - `infrastructure/persistence/**` for Spring Data repositories and JPA entities.
  - `infrastructure/config/**` for module-level Spring configuration/properties.
  - `infrastructure/api/handler/**` for module-specific API exception handlers when controllers still live under `infrastructure/api`.
- Avoid vague catch-all packages such as `integration` for new code. Use adapter direction (`adapter/in` or `adapter/out`) unless the class is not an adapter.
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
- `GET /api/v1/payments/plans`
- `POST /api/v1/payments/webhook/mercado-pago`

### Protected

- Any other endpoints under `/api/v1/**` not listed above

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
- `chat_session`
- `chat_message`
- `citations`
- `subscriptions`
- `token_transactions`
- `payment_webhook_events`

## Swagger / OpenAPI

- Swagger UI path: `/swagger-ui.html`
- API docs path: `/v3/api-docs`
- Protected endpoints should include OpenAPI security requirement (`bearerAuth`) so Swagger can send tokens.

## Implementation Conventions

- Prefer constructor injection.
- Keep the `I` prefix for interfaces, for example `IAuthUseCase`, `IChatPersistencePort`, and `IPaymentGatewayPort`.
- Keep controllers thin; business logic in services.
- Use DTOs for request/response payloads.
- Keep responses explicit with proper HTTP status codes.
- Avoid leaking internal exceptions/messages to clients.
- Application services should depend on project-owned ports, not concrete infrastructure implementations.
- If an infrastructure processor depends only on application abstractions and reacts to a technical trigger, place it as an inbound adapter.
- If a class implements an application abstraction using a concrete external system or framework, place it as an outbound adapter.
- Do not inject Spring `ApplicationEventPublisher`, HTTP clients, RabbitMQ clients, repositories, or SSE services directly into application services unless there is a deliberate architectural exception.

## Change Checklist

When adding or changing endpoints:

1. Update controller + service + DTOs.
2. Add/update `application` ports if new use cases/integrations are needed.
3. Add/update infrastructure adapters for outbound ports.
4. Add or update a domain-scoped exception handler (`@RestControllerAdvice(basePackages = "<domain-package>")`) for domain-specific errors (same pattern as `auth` and `chat`).
5. Update security rules in `SecurityConfig`.
6. For chat asynchronous flows, update RabbitMQ topology/properties/listeners if message contracts change.
7. For payment changes, keep Mercado Pago secrets in env and non-secret plan config in module resource files.
8. Update Swagger annotations (requests/responses/security requirement).
9. Ensure protected routes require Bearer token.
10. Update `README.md` with new endpoint usage.

When changing architecture/package structure:

1. Keep port packages split only by `in` and `out`.
2. Keep adapter implementations under `infrastructure/adapter/in` or `infrastructure/adapter/out`.
3. Keep persistence repositories/entities under `infrastructure/persistence`.
4. Keep configuration under `infrastructure/config` or `common/config`.
5. Update `ArchitectureRulesTest` when a package convention or module boundary becomes a rule.
6. Update `ARCHITECTURE_CODE_REVIEW.md` if the change resolves or changes a listed issue.

## Local Run

```bash
./mvnw spring-boot:run
```

App default URL: `http://localhost:8080`
