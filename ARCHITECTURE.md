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
- `ChatService` uses `ChatPersistencePort`, `ChatOutboxPort`, `ChatUserLookupPort`
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
- `model/ChatMessageProcessing`
- `model/ChatMessageProcessingStatus`
- `model/ChatCitation`
- `model/ChatOutboxEvent`
- `model/ChatOutboxEventStatus`
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
- `port/out/ChatOutboxPort`
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
- `integration/ChatOutboxRelay`
- `integration/ChatOutboxCleanupJob`
- `integration/ChatAsyncProcessor`
- `integration/ChatLocalAsyncProcessor`
- `integration/ChatMessageEventProcessor`
- `sse/ChatSseEmitterService`
- `persistence/ChatSessionRepository`
- `persistence/ChatMessageRepository`
- `persistence/ChatMessageProcessingRepository`
- `persistence/ChatCitationRepository`
- `persistence/ChatOutboxEventRepository`
- `persistence/entity/ChatSessionEntity`
- `persistence/entity/ChatMessageEntity`
- `persistence/entity/ChatMessageProcessingEntity`
- `persistence/entity/ChatCitationEntity`
- `persistence/entity/ChatOutboxEventEntity`
- `adapter/events/TransactionalChatOutboxAdapter`
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
- `port/out/PaymentPlanCatalogPort`
- `port/out/PaymentGatewayPort`
- `port/out/PaymentPersistencePort`
- `service/PaymentService`

#### `payment/infrastructure`
- `api/PaymentController`
- `api/PaymentWebhookController`
- `api/handler/PaymentExceptionHandler`
- `config/PaymentCatalog` (implements `PaymentPlanCatalogPort`)
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

General rules for all C4 diagrams in this repo:
- Name each relationship with an action-oriented label, not only the transport. Example: `Sends chat request`, `Reads/writes business data`, `Publishes queued chat events`.
- After the relationship name, describe the communication method in parentheses. Example: `(HTTPS JSON)`, `(AMQP)`, `(JPA/Hibernate over PostgreSQL)`, `(SSE over HTTPS)`.
- Do not duplicate relationships between the same two elements just because the internal use case is different. If one container both reads and writes to PostgreSQL, model one relationship such as `Reads/writes business and outbox data`.
- Keep relationship labels business-meaningful at Level 1 and Level 2. Move technical subflows such as outbox persistence, retry policy, or specific listeners to Level 3.
- Prefer one directional arrow per actual initiator. If communication is bidirectional in practice, model the dominant request direction unless the reverse path is independently important.

### 1) System Context (Level 1)
Model the backend as one system and include external actors/systems.

Actors:
- `Vulnerable User`: uses the app to ask legal questions
- `Administrator`: uploads and curates legal files directly in `n8n` (not in backend endpoints)

External systems:
- `Mercado Pago`: payment provider for subscription checkout and billing events
- `Gemini API`: LLM + file search used by the RAG flow

System under design:
- `LegalFam Web Platform`

Relationships:
- `Vulnerable User -> LegalFam Web Platform`: `Uses legal chat and subscription features` `(Browser interaction)`
- `Administrator -> LegalFam Web Platform`: `Uses administration and curation capabilities` `(Browser interaction)`
- `LegalFam Web Platform -> Mercado Pago`: `Uses subscription checkout and billing services` `(HTTPS API + webhooks)`
- `LegalFam Web Platform -> Gemini API`: `Uses AI retrieval and generation capabilities` `(Indirect AI workflow dependency)`

### 2) Containers (Level 2)
Define runtime containers:
- `Frontend Web Application`: browser-facing UI for end users and administrators
- `Spring Boot Application`: hosts `auth`, `chat`, `security`, `common`
- `n8n` (internal): orchestrates chat/RAG workflow, manages admin ingestion workflows
- `PostgreSQL`: stores `users`, `refresh_tokens`, `chat_session`, `chat_message`, `chat_message_processing`, `citations`, `chat_outbox_event`, `subscriptions`, `token_transactions`, `payment_webhook_events`
- `RabbitMQ` (internal): async event broker for chat
- `Mercado Pago` (external): subscription checkout and recurring payment notifications
- `Gemini API` (external)

Container connections:
- `Vulnerable User -> Frontend Web Application`: `Uses chat and subscription UI` `(Browser interaction)`
- `Administrator -> Frontend Web Application`: `Uses administration UI` `(Browser interaction)`
- `Frontend Web Application -> Spring Boot Application`: `Calls backend API and receives SSE updates` `(HTTPS JSON + SSE over HTTPS)`
- `Spring Boot Application -> PostgreSQL`: `Reads/writes users, auth, chat, payments, and chat outbox data` `(JPA/Hibernate over PostgreSQL)`
- `Spring Boot Application -> RabbitMQ`: `Publishes queued chat processing events` `(AMQP 0-9-1, publisher confirms)`
- `RabbitMQ -> Spring Boot Application`: `Delivers queued chat events to consumer` `(AMQP 0-9-1, durable quorum queue + DLQ)`
- `Spring Boot Application -> n8n`: `Coordinates assistant processing through queued chat flow` `(AMQP producer + HTTP JSON consumer flow)`
- `n8n -> Spring Boot Application`: `Returns assistant results indirectly through backend-managed persistence flow` `(HTTP response / integration callback semantics inside processing flow)`
- `Spring Boot Application -> Mercado Pago`: `Creates checkout sessions, cancels subscriptions, and queries gateway state` `(HTTPS REST/SDK)`
- `Mercado Pago -> Spring Boot Application`: `Sends subscription/payment webhooks` `(HTTPS webhook)`
- `n8n -> Gemini API`: `Executes retrieval and generation workflow` `(HTTPS API)`

Recommended relation naming pattern for Level 2:
- Format: ``Source -> Target: Verb + business object (technical channel)``
- Good examples:
  - `Spring Boot Application -> PostgreSQL: Reads/writes business and outbox data (JPA/Hibernate over PostgreSQL)`
  - `Spring Boot Application -> RabbitMQ: Publishes queued chat events (AMQP 0-9-1)`
  - `Mercado Pago -> Spring Boot Application: Sends payment webhooks (HTTPS webhook)`
- Avoid labels that are only technology names:
  - Bad: `Spring Boot API -> PostgreSQL (JPA/Hibernate)`
  - Better: `Spring Boot API -> PostgreSQL: Reads/writes business data (JPA/Hibernate over PostgreSQL)`

### 3) Components (Level 3)
Break the main runtime containers into relevant components. In this system, prioritize:
- `Spring Boot Application` components
- `n8n` workflow/integration components

Do not create a separate C4 component view for `common` or `security` as if they were business modules. Keep them inside the backend component view only when they are needed for understanding a concrete flow.

#### Auth Components
- API: `AuthController`, `AuthExceptionHandler`
- Application: `AuthUseCase`, `AuthService`
- Outbound ports: `UserPort`, `RefreshTokenPort`, `AccessTokenPort`, `TokenValidationPort`
- Adapters: `JpaUserAdapter`, `JpaRefreshTokenAdapter`, `JwtService`, `JwtAuthenticationFilter`
- Domain: `User`, `RefreshToken`, auth exceptions

#### Chat Components
- API: `ChatController`, `ChatExceptionHandler`
- Application: `ChatUseCase`, `ChatService`, `ChatAssistantPersistenceService`
- Ports: `ChatPersistencePort`, `ChatOutboxPort`, `ChatEventPublisherPort`, `ChatUserLookupPort`, `ChatAssistantPersistenceUseCase`
- Adapters/infra: `JpaChatPersistenceAdapter`, `TransactionalChatOutboxAdapter`, `SpringChatEventPublisherAdapter`, `RabbitChatEventPublisherAdapter`, `ChatOutboxRelay`, `ChatOutboxCleanupJob`, `N8nWebhookClient`, `ChatAsyncProcessor`, `ChatLocalAsyncProcessor`, `ChatMessageEventProcessor`, `ChatSseEmitterService`, `UserIdentityChatLookupAdapter`
- Domain: `ChatSession`, `ChatMessage`, `ChatMessageProcessing`, `ChatMessageProcessingStatus`, `ChatCitation`, `ChatOutboxEvent`, `ChatOutboxEventStatus`, `ChatMessageRole`, chat exceptions

#### Payment Components
- API: `PaymentController`, `PaymentWebhookController`, `PaymentExceptionHandler`
- Application: `PaymentUseCase`, `PaymentProvisioningUseCase`, `PaymentTokenUseCase`, `PaymentService`
- Ports: `PaymentPersistencePort`, `PaymentGatewayPort`, `PaymentPlanCatalogPort`
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
   - `ChatPersistencePort`, `ChatOutboxPort`, `ChatEventPublisherPort`, `ChatUserLookupPort`, `ChatAssistantPersistenceUseCase`
   - `JpaChatPersistenceAdapter`, `TransactionalChatOutboxAdapter`, `SpringChatEventPublisherAdapter`, `RabbitChatEventPublisherAdapter`, `UserIdentityChatLookupAdapter`
   - `ChatOutboxRelay`, `ChatOutboxCleanupJob`, `ChatAsyncProcessor`, `ChatLocalAsyncProcessor`, `ChatMessageEventProcessor`, `N8nWebhookClient`, `ChatSseEmitterService`
   - `ChatSession`, `ChatMessage`, `ChatMessageProcessing`, `ChatMessageProcessingStatus`, `ChatCitation`, `ChatOutboxEvent`, related entities
3. Show relations:
   - controller depends on `ChatUseCase`
   - service implements `ChatUseCase`
   - service depends on ports
   - adapters implement ports
   - `ChatService` writes the user message, processing record, and outbox row in one transaction
   - `ChatOutboxRelay` reads PostgreSQL, publishes to RabbitMQ, and updates outbox state
   - async processors consume/publish events and call application use cases

Optional quality constraints for both Level 4 diagrams:
- Stereotype interfaces as `<<port>>`
- Stereotype adapter classes as `<<adapter>>`
- Keep framework classes outside domain package
- Avoid showing utility/noise classes unless they are architectural boundaries

Modeling note for Level 3:
- This is the right level to show the chat outbox flow explicitly.
- Example component relationships worth naming:
  - `ChatService -> ChatPersistencePort`: `Persists session/message state`
  - `ChatService -> ChatOutboxPort`: `Registers queued chat event`
  - `TransactionalChatOutboxAdapter -> PostgreSQL`: `Stores outbox event`
  - `ChatOutboxRelay -> ChatEventPublisherPort`: `Publishes ready outbox event`
  - `RabbitChatEventPublisherAdapter -> RabbitMQ`: `Publishes chat.message.queued.v1`
  - `ChatAsyncProcessor -> ChatMessageEventProcessor`: `Consumes queued chat event`
  - `ChatMessageEventProcessor -> N8nWebhookClient`: `Requests assistant response`

## Suggested Diagram Set
- `c4-context-backend` (Level 1)
- Scope:
  - `Vulnerable User`
  - `Administrator`
  - `LegalFam Web App`
  - `Mercado Pago`
  - `Gemini API`
- Main idea:
  - users/admin interact with the web system, and the web system depends on backend capabilities plus external payment/AI ecosystem

- `c4-container-platform` (Level 2)
- Scope:
  - `Vulnerable User -> Frontend Web Application -> Spring Boot Application`
  - `Spring Boot Application <-> n8n`
  - `Spring Boot Application -> Mercado Pago`
  - `n8n -> Gemini API`
  - supporting internal containers: `PostgreSQL`, `RabbitMQ`

- `c4-component-backend` (Level 3)
- Scope:
  - backend components for `auth`, `chat`, `payment`
  - include `common` and `security` only as supporting internals when they clarify a flow, not as standalone business modules

- `c4-component-n8n` (Level 3)
- Scope:
  - chat/RAG workflow orchestration
  - ingestion/curation workflows
  - Gemini integration boundary

- `l4-class-auth.puml` (Level 4)
- `l4-class-chat.puml` (Level 4)
- `l4-class-payment.puml` (Level 4)
- Scope:
  - code diagrams should be organized by business module only: `auth`, `chat`, `payment`
  - do not create Level 4 module diagrams for `common` or `security`
