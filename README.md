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

1. Call `POST /api/auth/login` (or `signup`) and copy `accessToken`.
2. Click **Authorize** in Swagger.
3. Paste only the raw token (without `Bearer `).
4. Call protected endpoints (for example `GET /api/users`).

## Authentication Flow

- `POST /api/auth/signup` creates a user and returns tokens.
- `POST /api/auth/login` validates credentials and returns tokens.
- `POST /api/auth/refresh` rotates refresh token and returns new tokens.

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

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`

### Protected endpoints (Bearer token required)

- `GET /api/users`

## Example Requests

### Signup

```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

### Refresh

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}'
```

### Get all users (protected)

```bash
curl http://localhost:8080/api/users \
  -H "Authorization: Bearer <your_access_token>"
```

## Security Behavior

- Missing/invalid token on protected endpoints returns `401`.
- Authenticated but forbidden access returns `403`.
- Passwords are stored hashed with BCrypt.

## Token Expiration Defaults

Configured in `src/main/resources/application.properties`:

- Access token: `900000` ms (15 minutes)
- Refresh token: `604800000` ms (7 days)

Refresh token rotation is enabled: each successful refresh revokes the old refresh token and issues a new one.
