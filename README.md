# Backend API

Spring Boot backend with email/password authentication, JWT access tokens, refresh token rotation, PostgreSQL persistence, and Swagger UI.

## Tech Stack

- Java 21
- Spring Boot 4.0.5
- Spring Security
- Spring Data JPA
- Spring AMQP (RabbitMQ)
- PostgreSQL
- JWT (`io.jsonwebtoken`)
- Swagger / OpenAPI (`springdoc`)

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
JWT_SECRET=your-very-strong-secret-at-least-32-characters
CORS_ALLOWED_ORIGINS=*
N8N_WEBHOOK_URL=http://localhost:5678/webhook/chat-process
N8N_AUTH_HEADER_NAME=X-N8N-Token
N8N_AUTH_TOKEN=your-shared-secret
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
- `CORS_ALLOWED_ORIGINS=*` allows all origins (current default behavior).
- To allow only one origin later, set for example `CORS_ALLOWED_ORIGINS=http://localhost:3000`.
- `POST /api/v1/chat/send` enqueues message processing asynchronously.
- With `CHAT_RABBIT_ENABLED=true` (default), chat processing is EDA through RabbitMQ.
- With `CHAT_RABBIT_ENABLED=false`, the backend uses local async event processing.
- Database schema migrations are managed manually (not by Flyway).

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

### Protected endpoints (Bearer token required)

- `POST /api/v1/chat/sessions`
- `GET /api/v1/chat/subscribe/{sessionId}`
- `POST /api/v1/chat/send`
- `GET /api/v1/chat/sessions`
- `GET /api/v1/chat/sessions/{sessionId}/messages`
- `PATCH /api/v1/chat/messages/{messageId}/rating`

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
By default this request is published to RabbitMQ and processed asynchronously by a consumer.
`sessionId` is required.

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

## Token Expiration Defaults

Configured in `src/main/resources/application.properties`:

- Access token: `900000` ms (15 minutes)
- Refresh token: `604800000` ms (7 days)

Refresh token rotation is enabled: each successful refresh revokes the old refresh token and issues a new one.

## Architecture Docs

- See [ARCHITECTURE.md](ARCHITECTURE.md) for:
- Modular hexagonal structure (`domain`, `application`, `infrastructure`, `common`)
- C4 model (Context, Containers, Components, main classes)
- RabbitMQ EDA flow for chat module
