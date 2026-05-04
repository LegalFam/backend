# Backend Architecture

## Overview
This project uses a modular hexagonal architecture.

Business modules:
- `auth`
- `chat`
- `payment`

Cross-cutting module:
- `common`

For each business module:
- `domain`: core business models and domain exceptions (framework-agnostic)
- `application`: use cases, ports, DTOs, and application services
- `infrastructure`: controllers, adapters, repositories, integrations, and framework wiring

Dependency direction:
- `infrastructure -> application -> domain`
- `application` depends on abstractions (`ports`) only
- `infrastructure` implements outbound ports

## How It Is Implemented Here

### 1) Inbound flow (HTTP -> Use Case)
- Controllers live in `*/infrastructure/api`
- Controllers depend on `application/port/in/*UseCase`
- Application services implement use case interfaces

Examples:
- `AuthController -> AuthUseCase -> AuthService`
- `ChatController -> ChatUseCase -> ChatService`

### 2) Outbound flow (Use Case -> External systems)
- Application services call outbound ports in `application/port/out`
- Infrastructure adapters implement those ports for JPA, JWT, events, and integrations

Examples:
- `AuthService` uses `UserPort`, `RefreshTokenPort`, `AccessTokenPort`
- `ChatService` uses `ChatPersistencePort`, `ChatEventPublisherPort`, `ChatUserLookupPort`
- `PaymentService` uses `PaymentPersistencePort`, `PaymentGatewayPort`, `UserPort`

### 3) Domain model isolation
- Domain classes are plain Java classes in `domain/model`
- JPA entities are in `infrastructure/persistence/entity`
- Domain exceptions are in `domain/exception`

### 4) Cross-cutting concerns
- `common/config`: shared OpenAPI config
- `common/error`: shared API error contract and global error handling
- Security wiring is isolated in `security/infrastructure` (not in `common`)

## Project Structure

```text
src/main/java/com/legalfam/backend
|-- BackendApplication.java
|-- auth
|   |-- domain
|   |-- application
|   `-- infrastructure
|-- chat
|   |-- domain
|   |-- application
|   `-- infrastructure
|-- payment
|   |-- domain
|   |-- application
|   `-- infrastructure
|-- security
|   `-- infrastructure
`-- common
    |-- config
    `-- error
```

## Class Catalog (Main Source Set)

### Root
- `BackendApplication`

### `common`

#### `common/config`
- `OpenApiConfig`

#### `common/error`
- `ApiError` (record)
- `ApiErrorFactory`
- `handler/GlobalExceptionHandler`

### `security`

#### `security/infrastructure`
- `SecurityConfig`
- `JwtAuthenticationFilter`

### `auth`

#### `auth/domain`
- `model/User`
- `model/RefreshToken`
- `exception/EmailAlreadyExistsException`
- `exception/InvalidCredentialsException`
- `exception/InvalidRefreshTokenException`
- `exception/InvalidAuthRequestException`

#### `auth/application`
- `dto/LoginRequest`
- `dto/SignupRequest`
- `dto/RefreshTokenRequest`
- `dto/TokenResponse`
- `port/in/AuthUseCase`
- `port/out/UserPort`
- `port/out/RefreshTokenPort`
- `port/out/AccessTokenPort`
- `port/out/TokenValidationPort`
- `service/AuthService`

#### `auth/infrastructure`
- `api/AuthController`
- `api/handler/AuthExceptionHandler`
- `security/JwtService`
- `persistence/UserRepository`
- `persistence/RefreshTokenRepository`
- `persistence/entity/UserEntity`
- `persistence/entity/RefreshTokenEntity`
- `adapter/out/persistence/JpaUserAdapter`
- `adapter/out/persistence/JpaRefreshTokenAdapter`

### `chat`

#### `chat/domain`
- `model/ChatSession`
- `model/ChatMessage`
- `model/ChatCitation`
- `model/ChatMessageRole`
- `exception/ChatAccessDeniedException`
- `exception/ChatNotFoundException`
- `exception/ChatUpstreamException`
- `exception/InvalidChatRequestException`

#### `chat/application`
- `dto/ChatAskRequest`
- `dto/ChatAskResponse`
- `dto/ChatSendAcceptedResponse`
- `dto/ChatSessionResponse`
- `dto/ChatMessageResponse`
- `dto/ChatCitationResponse`
- `dto/ChatRateMessageRequest`
- `dto/ChatAssistantMessageDispatch`
- `dto/ChatAssistantErrorDispatch`
- `event/ChatMessageQueuedEvent`
- `event/ChatAssistantMessageEvent`
- `event/ChatAssistantErrorEvent`
- `port/in/ChatUseCase`
- `port/in/ChatAssistantPersistenceUseCase`
- `port/out/ChatPersistencePort`
- `port/out/ChatEventPublisherPort`
- `port/out/ChatUserLookupPort`
- `service/ChatService`
- `service/ChatAssistantPersistenceService`

#### `chat/infrastructure`
- `api/ChatController`
- `api/handler/ChatExceptionHandler`
- `config/AsyncConfig`
- `config/ChatRabbitConfig`
- `integration/N8nWebhookClient`
- `integration/ChatAsyncProcessor`
- `integration/ChatLocalAsyncProcessor`
- `integration/ChatMessageEventProcessor`
- `sse/ChatSseEmitterService`
- `persistence/ChatSessionRepository`
- `persistence/ChatMessageRepository`
- `persistence/ChatCitationRepository`
- `persistence/entity/ChatSessionEntity`
- `persistence/entity/ChatMessageEntity`
- `persistence/entity/ChatCitationEntity`
- `adapter/events/SpringChatEventPublisherAdapter`
- `adapter/events/RabbitChatEventPublisherAdapter`
- `adapter/persistence/JpaChatPersistenceAdapter`
- `adapter/persistence/UserIdentityChatLookupAdapter`

### `payment`

#### `payment/domain`
- `model/Subscription`
- `model/SubscriptionPlanCode`
- `model/SubscriptionStatus`
- `model/PaymentProvider`
- `model/TokenTransaction`
- `model/TokenTransactionType`
- `exception/InsufficientTokensException`
- `exception/InvalidPaymentRequestException`
- `exception/PaymentGatewayException`
- `exception/PaymentWebhookException`
- `exception/SubscriptionInactiveException`
- `exception/SubscriptionNotFoundException`

#### `payment/application`
- `dto/CreateCheckoutSessionRequest`
- `dto/CreateCheckoutSessionResponse`
- `dto/PaymentPlanDefinition`
- `dto/PaymentPlanResponse`
- `dto/PaymentSubscriptionResponse`
- `dto/PaymentSubscriptionSnapshot`
- `dto/PaymentWebhookNotification`
- `port/in/PaymentUseCase`
- `port/in/PaymentProvisioningUseCase`
- `port/in/PaymentTokenUseCase`
- `port/out/PaymentGatewayPort`
- `port/out/PaymentPersistencePort`
- `service/PaymentService`

#### `payment/infrastructure`
- `api/PaymentController`
- `api/PaymentWebhookController`
- `api/handler/PaymentExceptionHandler`
- `config/PaymentCatalog`
- `adapter/gateway/MercadoPagoPaymentGatewayAdapter`
- `adapter/persistence/JpaPaymentPersistenceAdapter`
- `adapter/persistence/PaymentEntityMapper`
- `persistence/SubscriptionRepository`
- `persistence/TokenTransactionRepository`
- `persistence/PaymentWebhookEventRepository`
- `persistence/entity/SubscriptionEntity`
- `persistence/entity/TokenTransactionEntity`
- `persistence/entity/PaymentWebhookEventEntity`

## Test Structure (Mirrors Modules)
- `auth/application/service/*`
- `auth/infrastructure/api/*`
- `security/infrastructure/*`
- `chat/infrastructure/api/*`
- `payment/infrastructure/api/*`
- `common/config/*`
- `common/error/*`
- `ArchitectureRulesTest`

## Practical Notes
- Implement business rules in `application/service`, exposed through `application/port/in`
- Add new external integrations in `infrastructure` implementing `application/port/out`
- Keep `common` only for truly cross-module concerns
- Keep `/api/v1/auth/**` public and all non-auth endpoints protected by JWT

## C4 Model Guide

This section explains how to model the backend with C4: Context -> Containers -> Components -> Code.

### 1) System Context (Level 1)
Model the backend as one system and include external actors/systems.

Actors:
- `Vulnerable User`: uses the app to ask legal questions
- `Administrator`: uploads and curates legal files directly in `n8n` (not in backend endpoints)

External systems:
- `Gemini API`: LLM + file search used by the RAG flow

System under design:
- `LegalFam Backend API` (Spring Boot)

Relationships:
- `Vulnerable User -> Backend API` (HTTPS JSON + SSE)
- `Backend API -> PostgreSQL` (JPA/Hibernate)
- `Backend API -> RabbitMQ` (AMQP)
- `Backend API -> n8n` (HTTP webhook)
- `n8n -> Gemini API` (HTTPS)
- `Administrator -> n8n` (direct ingestion/curation workflows)

### 2) Containers (Level 2)
Define runtime containers:
- `Spring Boot Application`: hosts `auth`, `chat`, `security`, `common`
- `n8n` (internal): orchestrates chat/RAG workflow, manages admin ingestion workflows
- `PostgreSQL`: stores `users`, `refresh_tokens`, `chat_session`, `chat_message`, `citations`, `subscriptions`, `token_transactions`, `payment_webhook_events`
- `RabbitMQ` (internal): async event broker for chat
- `Mercado Pago` (external): subscription checkout and recurring payment notifications
- `Gemini API` (external)

Container connections:
- `Vulnerable User -> Spring Boot API` (REST + SSE)
- `Spring Boot API -> PostgreSQL` (JPA/Hibernate)
- `Spring Boot API -> RabbitMQ` (publish `chat.message.queued.v1`)
- `RabbitMQ -> Spring Boot API` (consume `chat.message.queued.q`)
- `Spring Boot API -> Mercado Pago` (subscription checkout, cancellation, subscription lookup)
- `Spring Boot API -> n8n` (HTTP POST through `N8nWebhookClient`)
- `n8n -> Gemini API` (HTTPS)
- `Administrator -> n8n` (direct file upload/curation)

### 3) Components (Level 3)
Break Spring Boot into module components.

#### Auth Components
- API: `AuthController`, `AuthExceptionHandler`
- Application: `AuthUseCase`, `AuthService`
- Outbound ports: `UserPort`, `RefreshTokenPort`, `AccessTokenPort`, `TokenValidationPort`
- Adapters: `JpaUserAdapter`, `JpaRefreshTokenAdapter`, `JwtService`, `JwtAuthenticationFilter`
- Domain: `User`, `RefreshToken`, auth exceptions

#### Chat Components
- API: `ChatController`, `ChatExceptionHandler`
- Application: `ChatUseCase`, `ChatService`, `ChatAssistantPersistenceService`
- Ports: `ChatPersistencePort`, `ChatEventPublisherPort`, `ChatUserLookupPort`, `ChatAssistantPersistenceUseCase`
- Adapters/infra: `JpaChatPersistenceAdapter`, `SpringChatEventPublisherAdapter`, `RabbitChatEventPublisherAdapter`, `N8nWebhookClient`, `ChatAsyncProcessor`, `ChatLocalAsyncProcessor`, `ChatMessageEventProcessor`, `ChatSseEmitterService`, `UserIdentityChatLookupAdapter`
- Domain: `ChatSession`, `ChatMessage`, `ChatCitation`, `ChatMessageRole`, chat exceptions

#### Payment Components
- API: `PaymentController`, `PaymentWebhookController`, `PaymentExceptionHandler`
- Application: `PaymentUseCase`, `PaymentProvisioningUseCase`, `PaymentTokenUseCase`, `PaymentService`
- Ports: `PaymentPersistencePort`, `PaymentGatewayPort`
- Adapters/infra: `JpaPaymentPersistenceAdapter`, `MercadoPagoPaymentGatewayAdapter`, `PaymentCatalog`
- Domain: `Subscription`, `TokenTransaction`, payment exceptions

#### Common/Security Components
- Security: `security.infrastructure.SecurityConfig`, `security.infrastructure.JwtAuthenticationFilter`
- OpenAPI/Error: `common.config.OpenApiConfig`, `common.error.ApiError`, `common.error.ApiErrorFactory`, `common.error.handler.GlobalExceptionHandler`

### 4) Code / Class Diagrams (Level 4)
For Level 4 in this project, use PlantUML class diagrams per module.

Required diagrams:
- `l4-class-auth.puml`
- `l4-class-chat.puml`

Instruction for `auth` class diagram:
1. Include packages: `auth.domain`, `auth.application`, `auth.infrastructure`
2. Show main classes/interfaces:
   - `AuthController`, `AuthUseCase`, `AuthService`
   - `UserPort`, `RefreshTokenPort`, `AccessTokenPort`, `TokenValidationPort`
   - `JpaUserAdapter`, `JpaRefreshTokenAdapter`, `JwtService`
   - `User`, `RefreshToken`, `UserEntity`, `RefreshTokenEntity`
3. Show relations:
   - controller depends on `AuthUseCase`
   - service implements `AuthUseCase`
   - service depends on outbound ports
   - adapters implement outbound ports
   - persistence adapters map domain <-> entity

Instruction for `chat` class diagram:
1. Include packages: `chat.domain`, `chat.application`, `chat.infrastructure`
2. Show main classes/interfaces:
   - `ChatController`, `ChatUseCase`, `ChatService`, `ChatAssistantPersistenceService`
   - `ChatPersistencePort`, `ChatEventPublisherPort`, `ChatUserLookupPort`, `ChatAssistantPersistenceUseCase`
   - `JpaChatPersistenceAdapter`, `SpringChatEventPublisherAdapter`, `RabbitChatEventPublisherAdapter`, `UserIdentityChatLookupAdapter`
   - `ChatAsyncProcessor`, `ChatLocalAsyncProcessor`, `ChatMessageEventProcessor`, `N8nWebhookClient`, `ChatSseEmitterService`
   - `ChatSession`, `ChatMessage`, `ChatCitation`, related entities
3. Show relations:
   - controller depends on `ChatUseCase`
   - service implements `ChatUseCase`
   - service depends on ports
   - adapters implement ports
   - async processors consume/publish events and call application use cases

Optional quality constraints for both Level 4 diagrams:
- Stereotype interfaces as `<<port>>`
- Stereotype adapter classes as `<<adapter>>`
- Keep framework classes outside domain package
- Avoid showing utility/noise classes unless they are architectural boundaries

## Suggested Diagram Set
- `c4-context-backend` (Level 1)
- `c4-container-backend` (Level 2)
- `c4-component-auth` (Level 3)
- `c4-component-chat` (Level 3)
- `c4-component-payment` (Level 3)
- `c4-component-common-security` (Level 3)
- `l4-class-auth.puml` (Level 4)
- `l4-class-chat.puml` (Level 4)
