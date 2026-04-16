# Backend API

Spring Boot backend with email/password authentication, JWT access tokens, refresh token rotation, PostgreSQL persistence, and Swagger UI.

## Tech Stack

- Java 21
- Spring Boot 4.0.5
- Spring Security
- Spring Data JPA
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
```

Notes:
- `JWT_SECRET` must be at least 32 characters.
- 64+ random characters is recommended for production.

## Run Locally

```bash
./mvnw spring-boot:run
```

By default, the API runs at:

- `http://localhost:8080`

## Swagger

Open:

- `http://localhost:8080/swagger-ui.html`

### Testing protected endpoints in Swagger

1. Call `POST /api/v1/auth/login` (or `signup`) and copy `accessToken`.
2. Click **Authorize** in Swagger.
3. Paste only the raw token (without `Bearer `).
4. Call protected endpoints (for example `GET /api/v1/users`).

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

- `GET /api/v1/users`

## Example Requests

### Signup

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'
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

### Get all users (protected)

```bash
curl http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <your_access_token>"
```

## Security Behavior

- Missing/invalid token on protected endpoints returns `401`.
- Authenticated but forbidden access returns `403`.
- Passwords are stored hashed with BCrypt.

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

## Token Expiration Defaults

Configured in `src/main/resources/application.properties`:

- Access token: `900000` ms (15 minutes)
- Refresh token: `604800000` ms (7 days)

Refresh token rotation is enabled: each successful refresh revokes the old refresh token and issues a new one.
