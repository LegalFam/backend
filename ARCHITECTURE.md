# Backend Architecture

## Overview
This project is organized with a **modular hexagonal architecture**.

Each business module (`auth`, `chat`, `user`) is split into:
- `domain`: core business model and domain exceptions.
- `application`: use cases, ports, and application services.
- `infrastructure`: adapters, controllers, persistence repositories, integrations, and framework wiring.

Shared cross-cutting concerns are in:
- `common`: global configuration and generic API error handling.

The dependency direction is:
- `infrastructure -> application -> domain`
- `application` depends on abstractions (`ports`), not concrete framework classes.
- `infrastructure` provides concrete implementations of outbound ports.

## How It Is Implemented Here

### 1) Inbound flow (HTTP -> Use Case)
- Controllers live in `*/infrastructure/api`.
- Controllers depend on `application/port/in/*UseCase` interfaces.
- Application services implement those use case interfaces.

Examples:
- `AuthController -> AuthUseCase -> AuthService`
- `ChatController -> ChatUseCase -> ChatService`
- `UserController -> UserQueryUseCase -> UserService`

### 2) Outbound flow (Use Case -> External systems)
- Application services call outbound ports in `application/port/out`.
- Infrastructure adapters implement those ports and connect to JPA repositories, Spring events, JWT, etc.

Examples:
- `AuthService` uses `AuthUserPort`, `RefreshTokenPort`, `AccessTokenPort`.
- `ChatService` uses `ChatPersistencePort`, `ChatEventPublisherPort`.
- `UserService` uses `UserReadPort`.

### 3) Domain model isolation
- JPA entities are stored in each module `domain/model`.
- Domain exceptions are in `domain/exception`.
- Business rules stay in application services, not in common config.

### 4) Cross-cutting concerns in `common`
- `common/config`: shared security and OpenAPI configuration.
- `common/error`: shared API error contract and global error handling.

### 5) Module-scoped config placement
- Chat async executor config is module-local:
  - `chat/infrastructure/config/AsyncConfig`
- Auth JWT filter is module-local:
  - `auth/infrastructure/security/JwtAuthenticationFilter`
- Shared app-level config remains in:
  - `common/config/SecurityConfig`
  - `common/config/OpenApiConfig`

## Project Structure

```text
src/main/java/com/legalfam/backend
├── BackendApplication.java
├── auth
│   ├── domain
│   ├── application
│   └── infrastructure
├── chat
│   ├── domain
│   ├── application
│   └── infrastructure
├── user
│   ├── domain
│   ├── application
│   └── infrastructure
└── common
    ├── config
    └── error
```

## Class Catalog (Main Source Set)

### Root
- `BackendApplication`

### `common`

#### `common/config`
- `SecurityConfig`
- `OpenApiConfig`

#### `common/error`
- `ApiError` (record)
- `ApiErrorFactory`
- `exception/InvalidRequestException`
- `handler/GlobalExceptionHandler`

### `auth`

#### `auth/domain`
- `model/RefreshToken`
- `exception/EmailAlreadyExistsException`
- `exception/InvalidCredentialsException`
- `exception/InvalidRefreshTokenException`

#### `auth/application`
- `dto/LoginRequest`
- `dto/SignupRequest`
- `dto/RefreshTokenRequest`
- `dto/TokenResponse`
- `port/in/AuthUseCase`
- `port/out/AccessTokenPort`
- `port/out/AuthUserPort`
- `port/out/RefreshTokenPort`
- `port/out/TokenValidationPort`
- `service/AuthService`

#### `auth/infrastructure`
- `api/AuthController`
- `api/handler/AuthExceptionHandler`
- `security/JwtService`
- `security/JwtAuthenticationFilter`
- `persistence/RefreshTokenRepository`
- `adapter/out/persistence/JpaAuthUserAdapter`
- `adapter/out/persistence/JpaRefreshTokenAdapter`

### `user`

#### `user/domain`
- `model/User`

#### `user/application`
- `dto/UserResponse`
- `port/in/UserQueryUseCase`
- `port/out/UserReadPort`
- `service/UserService`

#### `user/infrastructure`
- `api/UserController`
- `persistence/UserRepository`
- `adapter/out/persistence/JpaUserReadAdapter`

### `chat`

#### `chat/domain`
- `model/ChatSession`
- `model/ChatMessage`
- `model/ChatCitation`
- `model/ChatMessageRole`
- `exception/ChatAccessDeniedException`
- `exception/ChatNotFoundException`
- `exception/ChatUpstreamException`

#### `chat/application`
- `dto/ChatAskRequest`
- `dto/ChatAskResponse`
- `dto/ChatSendAcceptedResponse`
- `dto/ChatSessionResponse`
- `dto/ChatMessageResponse`
- `dto/ChatCitationResponse`
- `dto/ChatRateMessageRequest`
- `event/ChatMessageQueuedEvent`
- `event/ChatAssistantMessageEvent`
- `event/ChatAssistantErrorEvent`
- `port/in/ChatUseCase`
- `port/out/ChatPersistencePort`
- `port/out/ChatEventPublisherPort`
- `service/ChatService`
- `service/ChatAssistantPersistenceService`

#### `chat/infrastructure`
- `api/ChatController`
- `api/handler/ChatExceptionHandler`
- `config/AsyncConfig`
- `integration/N8nWebhookClient`
- `integration/ChatAsyncProcessor`
- `sse/ChatSseEmitterService`
- `persistence/ChatSessionRepository`
- `persistence/ChatMessageRepository`
- `persistence/ChatCitationRepository`
- `adapter/events/SpringChatEventPublisherAdapter`
- `adapter/persistence/JpaChatPersistenceAdapter`

## Test Structure (Mirrors Modules)
- `auth/application/service/*`
- `auth/infrastructure/api/*`
- `auth/infrastructure/security/*`
- `chat/infrastructure/api/*`
- `common/config/*`
- `common/error/*`
- `user/infrastructure/api/*`

## Practical Notes
- New business rules should be implemented in `application/service` and exposed through `application/port/in`.
- New external integrations (DB/API/events) should be added as adapters in `infrastructure`, implementing `application/port/out`.
- `common` should only contain truly cross-module concerns.

## C4 Model Guide For This Backend

This section explains how to model this project with C4: **Context -> Containers -> Components -> Code (main classes)**.

### 1) System Context (C4 Level 1)
Model the backend as a single system and show external actors/systems:
- **Actors**
- Web client / mobile client (consumes REST + SSE)
- **External systems**
- PostgreSQL (persistent data)
- n8n webhook endpoint (chat assistant upstream)
- **System under design**
- `LegalFam Backend API` (Spring Boot service)

Relationships:
- Client -> Backend API (`HTTPS`, JSON REST, SSE for chat updates)
- Backend API -> PostgreSQL (`JPA/Hibernate`)
- Backend API -> n8n (`HTTP POST webhook`)

### 2) Containers (C4 Level 2)
Inside the backend system, define runtime/deployment containers:
- **Spring Boot Application Container**
- Hosts modules: `auth`, `chat`, `user`, `common`
- Exposes endpoints under `/api/v1/**`
- Handles JWT auth, business logic, validation, error mapping
- **PostgreSQL Container**
- Stores domain entities/tables (`users`, `refresh_tokens`, chat tables)
- **External n8n Container** (outside ownership boundary)
- Produces assistant responses consumed asynchronously

Optional infra containers (if modeled):
- API Gateway / Reverse Proxy (if used in deployment)
- Observability stack (logs/metrics/traces)

### 3) Components (C4 Level 3)
Break the Spring Boot container into module components by responsibility:

#### Auth Components
- API: `AuthController`, `AuthExceptionHandler`
- Application: `AuthUseCase`, `AuthService`
- Outbound ports: `AuthUserPort`, `RefreshTokenPort`, `AccessTokenPort`, `TokenValidationPort`
- Adapters: `JpaAuthUserAdapter`, `JpaRefreshTokenAdapter`, `JwtService`, `JwtAuthenticationFilter`
- Domain: `RefreshToken`, auth domain exceptions

#### Chat Components
- API: `ChatController`, `ChatExceptionHandler`
- Application: `ChatUseCase`, `ChatService`, `ChatAssistantPersistenceService`
- Outbound ports: `ChatPersistencePort`, `ChatEventPublisherPort`
- Adapters/infra: `JpaChatPersistenceAdapter`, `SpringChatEventPublisherAdapter`, `N8nWebhookClient`, `ChatAsyncProcessor`, `ChatSseEmitterService`
- Domain: `ChatSession`, `ChatMessage`, `ChatCitation`, `ChatMessageRole`, chat exceptions

#### User Components
- API: `UserController`
- Application: `UserQueryUseCase`, `UserService`
- Outbound port: `UserReadPort`
- Adapter: `JpaUserReadAdapter`, `UserRepository`
- Domain: `User`

#### Common Components
- Security/config: `SecurityConfig`, `OpenApiConfig`
- Error handling: `ApiError`, `ApiErrorFactory`, `GlobalExceptionHandler`, `InvalidRequestException`

### 4) Code / Main Classes (C4 Level 4)
For C4 level 4, focus on classes that carry business behavior and boundaries:
- **Entry points**
- `auth.infrastructure.api.AuthController`
- `chat.infrastructure.api.ChatController`
- `user.infrastructure.api.UserController`
- **Core use-case services**
- `auth.application.service.AuthService`
- `chat.application.service.ChatService`
- `chat.application.service.ChatAssistantPersistenceService`
- `user.application.service.UserService`
- **Boundary abstractions (ports)**
- all `application.port.in.*`
- all `application.port.out.*`
- **Infrastructure implementations**
- `auth.infrastructure.security.JwtService`
- `chat.infrastructure.integration.N8nWebhookClient`
- `*.infrastructure.adapter.*`
- `*.infrastructure.persistence.*`
- **Shared control plane**
- `common.config.SecurityConfig`
- `common.error.handler.GlobalExceptionHandler`

### Suggested Diagram Set
- `c4-context-backend` (Level 1)
- `c4-container-backend` (Level 2)
- `c4-component-auth`, `c4-component-chat`, `c4-component-user`, `c4-component-common` (Level 3)
- Optional class diagrams for:
- `auth.application.service.AuthService`
- `chat.application.service.ChatService`
- `chat.infrastructure.integration.ChatAsyncProcessor`
