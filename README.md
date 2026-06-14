# Backend API

Spring Boot backend with email/password authentication, JWT access tokens, refresh token rotation, PostgreSQL persistence, Mercado Pago-backed subscriptions, token-based chat entitlements, and Swagger UI.

## Tech Stack

- Java 21
- Spring Boot 4.0.6
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring AMQP (RabbitMQ)
- PostgreSQL
- Mercado Pago
- JWT (`io.jsonwebtoken`)
- Swagger / OpenAPI (`springdoc`)

## Architecture Notes

The backend is organized as a modular hexagonal Spring Boot application under `src/main/java/com/legalfam/backend`.

- Business modules currently include `auth`, `chat`, `payment`, and the planned/related `user` boundary.
- Shared cross-cutting API/config/error/security concerns live under `common`.
- Domain code belongs in `domain`.
- Use cases, DTOs, events, and ports belong in `application`.
- Infrastructure code belongs in `infrastructure`.
- Application ports are split by direction:
  - `application/port/in` for use cases called by inbound adapters.
  - `application/port/out` for dependencies implemented by outbound adapters.
- Infrastructure adapters should be classified by direction:
  - `infrastructure/adapter/in` for event handlers and message listeners that trigger application behavior.
  - `infrastructure/adapter/out` for persistence adapters, external clients, event publishers, catalogs, and gateway implementations.
- REST controllers live under `infrastructure/api`.
- Scheduled/background workers live under `infrastructure/worker`.
- Client event delivery support lives under responsibility-based infrastructure folders such as `infrastructure/delivery`.
- Spring Data repositories and JPA entities remain under `infrastructure/persistence`.
- Spring configuration remains under `infrastructure/config` or `common/config`.

Project convention: interfaces use the instructor-required `I` prefix, for example `IAuthUseCase`, `IChatPersistencePort`, and `IPaymentGatewayPort`.

For the current architecture review and staged remediation plan, see [`ARCHITECTURE_CODE_REVIEW.md`](ARCHITECTURE_CODE_REVIEW.md).

### Suggested Remediation Order

1. Stabilize package structure and adapter direction.
2. Restore application-to-infrastructure boundaries.
3. Reduce cross-module application coupling.
4. Fix critical payment consistency and webhook security.
5. Introduce typed validation and configuration.
6. Improve domain modeling and service cohesion.
7. Improve API scalability and documentation maintainability.
8. Add architecture regression tests.

## Requirements

- JDK 21
- PostgreSQL database
- Maven Wrapper (`./mvnw` is included)

## Environment Variables

Create a `.env` file in the project root:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=your_db
DB_USER=your_user
DB_PASSWORD=your_password
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
JWT_SECRET=your-very-strong-secret-at-least-32-characters
CORS_ALLOWED_ORIGINS=*
N8N_WEBHOOK_URL=http://localhost:5678/webhook/chat-process
N8N_AUTH_HEADER_NAME=X-N8N-Token
N8N_AUTH_TOKEN=your-shared-secret
MERCADO_PAGO_ACCESS_TOKEN=APP_USR-your-access-token
MERCADO_PAGO_WEBHOOK_SECRET=your-webhook-secret
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VHOST=/
CHAT_RABBIT_ENABLED=true
```

Notes:
- `JWT_SECRET` must be at least 32 characters.
- 64+ random characters is recommended for production.
- `DB_POOL_MAX_SIZE` controls the Hikari connection pool size. Keep it at or above expected concurrent HTTP, async chat, RabbitMQ, and scheduled DB work, while staying under your PostgreSQL provider limit.
- `CORS_ALLOWED_ORIGINS=*` allows all origins (current default behavior).
- To allow only one origin later, set for example `CORS_ALLOWED_ORIGINS=http://localhost:3000`.
- For the chat module, endpoint URLs and credentials still come from env (`N8N_*`, `RABBITMQ_*`), while non-secret chat settings are bound through typed properties from `src/main/resources/chat/chat.properties`.
- For the payment module, `MERCADO_PAGO_ACCESS_TOKEN` is required for gateway calls; `MERCADO_PAGO_WEBHOOK_SECRET` enables webhook signature verification.
- Non-secret payment configuration lives in `src/main/resources/payment/payment.properties`.
- API request bodies use Bean Validation and return the standard error shape for invalid fields.
- `POST /api/v1/chat/send` enqueues message processing asynchronously.
- With `CHAT_RABBIT_ENABLED=true` (default), chat processing uses `Transactional Outbox + RabbitMQ`.
- With `CHAT_RABBIT_ENABLED=false`, the backend still uses the same transactional outbox, but the relay dispatches locally after commit.
- Database schema is managed manually (not by Flyway).
- Apply [`database/schema.sql`](database/schema.sql) before using the app outside the test profile.

## Run Locally

```bash
./mvnw spring-boot:run
```

By default, the API runs at:

- `http://localhost:8080`

Use the test profile (internal test config in `application-test.properties`):

- Create `.env.test` and adjust values.
- And then use:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

## Swagger

Open:

- `http://localhost:8080/swagger-ui.html`

### Testing protected endpoints in Swagger

1. Call `POST /api/v1/auth/login` (or `signup`) and copy `accessToken`.
2. Click **Authorize** in Swagger.
3. Paste only the raw token (without `Bearer `).
4. Call protected endpoints (for example `POST /api/v1/chat/sessions`).

## Authentication Flow

- `POST /api/v1/auth/signup` creates a user and returns tokens.
- `POST /api/v1/auth/login` validates credentials and returns tokens.
- `POST /api/v1/auth/refresh` rotates refresh token and returns new tokens.

Token response format:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

## API Endpoints

### Public endpoints (no token required)

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/payments/plans`
- `POST /api/v1/payments/webhook/mercado-pago`

### Protected endpoints (Bearer token required)

- `POST /api/v1/chat/sessions`
- `GET /api/v1/chat/subscribe/{sessionId}`
- `POST /api/v1/chat/send`
- `GET /api/v1/chat/sessions?size={size}&cursor={nextCursor}`
- `GET /api/v1/chat/sessions/{sessionId}/messages?size={size}&cursor={nextCursor}`
- `PATCH /api/v1/chat/messages/{messageId}/rating`
- `PATCH /api/v1/chat/messages/{messageId}/receipt`
- `GET /api/v1/payments/subscription`
- `POST /api/v1/payments/checkout-sessions`
- `POST /api/v1/payments/subscription/cancel`

## Example Requests

### Signup

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!","name":"Juan Perez","phone":"900000000"}'
```

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

### Refresh

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}'
```

### Send chat message (protected, async)

Configure `N8N_WEBHOOK_URL` (`N8N_AUTH_TOKEN` optional).  
By default this request writes the user message, token consumption, session update, and an outbox row in the same database transaction. A relay then publishes the queued event asynchronously to RabbitMQ (or local async processing when Rabbit is disabled).
`sessionId` is required and each accepted request consumes `1` monthly token. If assistant processing fails later, that token is refunded automatically.

```bash
curl -X POST http://localhost:8080/api/v1/chat/send \
  -H "Authorization: Bearer <your_access_token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"Summarize this user message and return the next legal step.","sessionId":"<session_id>"}'
```

Response format:

```json
{
  "sessionId": "b0a8f12a-a605-4556-bef9-35f7868f7a3a",
  "userMessageId": "e6f3b5e6-24eb-49b7-8ee5-860eaab9454c",
  "status": "PROCESSING"
}
```

### List available plans (public)

```bash
curl http://localhost:8080/api/v1/payments/plans
```

Authenticated users may also send a Bearer token so the response can mark the current plan.

Response format:

```json
[
  {
    "code": "FREE",
    "billingInterval": "month",
    "monthlyPriceCents": null,
    "currency": "pen",
    "monthlyTokenLimit": 50,
    "currentPlan": true,
    "purchasable": true
  },
  {
    "code": "BASIC",
    "billingInterval": "month",
    "monthlyPriceCents": 1499,
    "currency": "pen",
    "monthlyTokenLimit": 500,
    "currentPlan": false,
    "purchasable": true
  }
]
```

### List chat sessions (protected, cursor-based)

```bash
curl "http://localhost:8080/api/v1/chat/sessions?size=20" \
  -H "Authorization: Bearer <your_access_token>"
```

For the next batch, call:

```bash
curl "http://localhost:8080/api/v1/chat/sessions?size=20&cursor=<nextCursor>" \
  -H "Authorization: Bearer <your_access_token>"
```

Response format:

```json
{
  "content": [
    {
      "id": "b0a8f12a-a605-4556-bef9-35f7868f7a3a",
      "title": "Consulta laboral",
      "createdAt": "2026-05-01T00:00:00Z",
      "updatedAt": "2026-05-01T00:10:00Z"
    }
  ],
  "nextCursor": "MQ"
}
```

Use `nextCursor` as the `cursor` query parameter on the next request. When `nextCursor` is `null`,
there are no more results. `size` must be between `1` and `100`.

### List chat messages (protected, cursor-based)

```bash
curl "http://localhost:8080/api/v1/chat/sessions/<sessionId>/messages?size=20" \
  -H "Authorization: Bearer <your_access_token>"
```

The response uses the same cursor envelope as chat sessions, with chat messages in `content`.

### Get subscription + token status (protected)

```bash
curl http://localhost:8080/api/v1/payments/subscription \
  -H "Authorization: Bearer <your_access_token>"
```

Response format:

```json
{
  "planCode": "FREE",
  "status": "ACTIVE",
  "provider": "FREE",
  "currentPeriodStart": "2026-05-01T00:00:00Z",
  "currentPeriodEnd": "2026-06-01T00:00:00Z",
  "cancelAtPeriodEnd": false,
  "monthlyTokenLimit": 50,
  "remainingTokens": 49
}
```

### Create Mercado Pago checkout link (protected)

```bash
curl -X POST http://localhost:8080/api/v1/payments/checkout-sessions \
  -H "Authorization: Bearer <your_access_token>" \
  -H "Content-Type: application/json" \
  -d '{"planCode":"BASIC","successUrl":"http://localhost:3000/billing/success"}'
```

Response format:

```json
{
  "url": "https://www.mercadopago.com.pe/subscriptions/checkout"
}
```

### Cancel current subscription (protected)

```bash
curl -X POST http://localhost:8080/api/v1/payments/subscription/cancel \
  -H "Authorization: Bearer <your_access_token>"
```

### Create chat session (protected)

```bash
curl -X POST http://localhost:8080/api/v1/chat/sessions \
  -H "Authorization: Bearer <your_access_token>"
```

### Subscribe to assistant responses (protected, SSE)

`sessionId` is the chat session id:

```bash
curl -N http://localhost:8080/api/v1/chat/subscribe/<sessionId> \
  -H "Authorization: Bearer <your_access_token>" \
  -H "Accept: text/event-stream"
```

SSE event types:
- `connected`
- `heartbeat`
- `assistant_message`
- `assistant_error` (when upstream processing fails)

Recommended new-chat flow:
1. `POST /api/v1/chat/sessions`
2. `GET /api/v1/chat/subscribe/{sessionId}`
3. `POST /api/v1/chat/send` with that `sessionId`

## Security Behavior

- Missing/invalid token on protected endpoints returns `401`.
- Authenticated but forbidden access returns `403`.
- Passwords are stored hashed with BCrypt.
- Signup requires `email`, `password`, `name`, and `phone`.
- `/api/v1/payments/webhook/mercado-pago` is public for Mercado Pago webhook delivery.

## Error Response Format

All API errors use a standardized JSON shape:

```json
{
  "type": "authentication_error",
  "code": "invalid_credentials",
  "message": "Invalid credentials",
  "status": 401,
  "path": "/api/v1/auth/login",
  "timestamp": "2026-04-15T12:34:56.789Z"
}
```

Examples of error codes:
- `invalid_request`
- `malformed_json`
- `email_already_exists`
- `invalid_credentials`
- `invalid_refresh_token`
- `unauthorized`
- `forbidden`
- `upstream_service_unavailable`
- `subscription_inactive`
- `insufficient_tokens`
- `payment_gateway_unavailable`
- `invalid_webhook`

## Token Expiration Defaults

Configured in `src/main/resources/application.properties`:

- Access token: `900000` ms (15 minutes)
- Refresh token: `604800000` ms (7 days)

Refresh token rotation is enabled: each successful refresh revokes the old refresh token and issues a new one.

## Subscription Plans

- `FREE`: `50` monthly tokens, reset monthly on the signup anniversary.
- `BASIC`: `500` monthly tokens, `PEN 14.99`, reset on the Mercado Pago billing cycle.
- `PREMIUM`: `2500` monthly tokens, `PEN 49.99`, reset on the Mercado Pago billing cycle.
- Users start on `FREE` immediately after signup.
- Chat tokens are deducted when `POST /api/v1/chat/send` is accepted.
- Failed assistant processing refunds the token tied to that user message.

## Chat Delivery Model

- `POST /api/v1/chat/send` keeps the public response contract stable and still returns `PROCESSING`.
- The backend persists the user message, creates a separate internal processing record in state `QUEUED`, consumes one token, updates the session, and triggers the `n8n` webhook asynchronously after commit.
- When the assistant response is persisted, the backend creates a `chat_outbox_event` row for delivery to the user with states `PENDING`, `PUBLISHED`, or `READ`.
- `ChatDeliveryRetryWorker` polls ready outbox rows every `5s` in batches of `50` using row locking and republishes assistant delivery events.
- Delivery retries are scheduled every `10` minutes while the assistant message remains unread.
- RabbitMQ is configured with a durable quorum queue, DLX/DLQ, publisher confirms, and mandatory returns for assistant delivery events.
- `PATCH /api/v1/chat/messages/{messageId}/receipt` marks the assistant message as `READ`.
- While a session has an assistant response with outbox state different from `READ`, `POST /api/v1/chat/send` is rejected for that same session.
