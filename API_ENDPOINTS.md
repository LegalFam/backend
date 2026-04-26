# API Endpoints (Frontend Guide)

Base URL: `http://localhost:8080`  
API Prefix: `/api/v1`

## Auth

### `POST /api/v1/auth/signup`
Create a user and return tokens.

Request body:
```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "name": "Juan Perez",
  "phone": "900000000"
}
```

Success response `201`:
```json
{
  "accessToken": "jwt-token",
  "refreshToken": "refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Common errors:
- `400` invalid request (`email/password/name/phone` required, invalid email format)
- `409` email already exists

### `POST /api/v1/auth/login`
Login and return tokens.

Request body:
```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

Success response `200`: same token response as signup.

Common errors:
- `400` invalid request
- `401` invalid credentials

### `POST /api/v1/auth/refresh`
Rotate refresh token and return a new token pair.

Request body:
```json
{
  "refreshToken": "refresh-token"
}
```

Success response `200`: same token response as signup/login.

Common errors:
- `400` refresh token missing
- `401` invalid/expired/revoked refresh token

## Protected Endpoints

Use header:
```http
Authorization: Bearer <accessToken>
```

### `GET /api/v1/users`
Get all users.

Success response `200`:
```json
[
  {
    "id": "uuid",
    "email": "user@example.com",
    "name": "Juan Perez",
    "phone": "900000000"
  }
]
```

### `POST /api/v1/chat/send`
Send user message for asynchronous processing.  
`sessionId` is required.

Request body:
```json
{
  "message": "De acuerdo con el Codigo Civil, como se calcula la pension?",
  "sessionId": "uuid"
}
```

Success response `202`:
```json
{
  "sessionId": "uuid",
  "userMessageId": "uuid",
  "status": "PROCESSING"
}
```

Notes:
- Backend persists the user message first, then calls n8n asynchronously.
- Assistant response is persisted in DB before SSE dispatch is attempted.
- If SSE is disconnected, frontend can recover data from `GET /api/v1/chat/sessions/{sessionId}/messages`.

Common errors:
- `400` `message` missing/blank
- `400` `sessionId` missing
- `403` session belongs to another user

### `POST /api/v1/chat/sessions`
Create a new chat session.

Success response `201`:
```json
{
  "id": "uuid",
  "createdAt": "2026-04-19T10:00:00Z",
  "updatedAt": "2026-04-19T10:00:00Z"
}
```

### `GET /api/v1/chat/subscribe/{sessionId}`
Subscribe to assistant events using Server-Sent Events (SSE).

Success response `200` with `text/event-stream`.

Expected events:
- `connected`
- `heartbeat`
- `assistant_message`
- `assistant_error`

Common errors:
- `403` session belongs to another user
- `404` session not found

### `GET /api/v1/chat/sessions`
List chat sessions for current user.

Success response `200`:
```json
[
  {
    "id": "uuid",
    "createdAt": "2026-04-19T10:00:00Z",
    "updatedAt": "2026-04-19T10:03:00Z"
  }
]
```

### `GET /api/v1/chat/sessions/{sessionId}/messages`
List messages in one session (ordered oldest to newest).

Success response `200`:
```json
[
  {
    "id": "uuid",
    "role": "USER",
    "content": "Hola",
    "rating": null,
    "createdAt": "2026-04-19T10:00:00Z",
    "citations": []
  },
  {
    "id": "uuid",
    "role": "ASSISTANT",
    "content": "Hola, en que puedo ayudarte?",
    "rating": 5,
    "createdAt": "2026-04-19T10:00:02Z",
    "citations": []
  }
]
```

Common errors:
- `403` session belongs to another user
- `404` session not found

Failure behavior:
- If upstream n8n fails (for example `404`), backend persists a `SYSTEM` message in the chat with an error text.
- The same failure is emitted over SSE as `assistant_error`.

### `PATCH /api/v1/chat/messages/{messageId}/rating`
Rate a message from 1 to 5.

Request body:
```json
{
  "rating": 5
}
```

Success response `200` with empty body.

Common errors:
- `400` rating missing or outside `1..5`
- `403` message belongs to another user
- `404` message not found

## Standard Error Shape

All error responses return:
```json
{
  "type": "validation_error",
  "code": "invalid_request",
  "message": "Message is required",
  "status": 400,
  "path": "/api/v1/chat/send",
  "timestamp": "2026-04-19T12:00:00.000Z"
}
```

## Frontend Integration Notes

- Store `accessToken` + `refreshToken` securely on login/signup.
- Retry failed protected calls after `POST /auth/refresh` on `401`.
- Recommended flow for new chats:
1. `POST /api/v1/chat/sessions`
2. `GET /api/v1/chat/subscribe/{sessionId}`
3. `POST /api/v1/chat/send` with `sessionId`
- Keep and reuse `sessionId` for all follow-up `/chat/send` requests.
- Keep an open SSE connection with `GET /api/v1/chat/subscribe/{sessionId}` for real-time assistant events.
- Use assistant `messageId` from listed messages if you plan to send ratings.
