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
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "name": "Juan Perez",
    "phone": "900000000"
  }
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

## Public Endpoints

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/payments/plans`
- `POST /api/v1/payments/webhook/mercado-pago`

## Protected Endpoints

Use header:
```http
Authorization: Bearer <accessToken>
```

- `GET /api/v1/payments/subscription`
- `POST /api/v1/payments/checkout-sessions`
- `POST /api/v1/payments/subscription/cancel`
- `POST /api/v1/chat/sessions`
- `PATCH /api/v1/chat/sessions/{sessionId}`
- `DELETE /api/v1/chat/sessions/{sessionId}`
- `GET /api/v1/chat/processing-status`
- `GET /api/v1/chat/subscribe/{sessionId}`
- `GET /api/v1/chat/sessions?size={size}&cursor={nextCursor}`
- `GET /api/v1/chat/sessions/{sessionId}/messages?size={size}&cursor={nextCursor}`
- `POST /api/v1/chat/send`
- `PATCH /api/v1/chat/messages/{messageId}/rating`
- `PATCH /api/v1/chat/messages/{messageId}/receipt`

## Payments

### `GET /api/v1/payments/plans`
Return the available plans. This endpoint is public. Authentication is optional; authenticated requests include `currentPlan`.

Success response `200`:
```json
[
  {
    "code": "FREE",
    "displayName": "Free",
    "description": "Starter access with a limited monthly token balance.",
    "billingInterval": "month",
    "monthlyPriceCents": null,
    "currency": "pen",
    "monthlyTokenLimit": 50,
    "currentPlan": true,
    "purchasable": true
  },
  {
    "code": "BASIC",
    "displayName": "Basic",
    "description": "Recurring subscription with 500 monthly chat tokens.",
    "billingInterval": "month",
    "monthlyPriceCents": 1499,
    "currency": "pen",
    "monthlyTokenLimit": 500,
    "currentPlan": false,
    "purchasable": true
  },
  {
    "code": "PREMIUM",
    "displayName": "Premium",
    "description": "Recurring subscription with 2500 monthly chat tokens.",
    "billingInterval": "month",
    "monthlyPriceCents": 4999,
    "currency": "pen",
    "monthlyTokenLimit": 2500,
    "currentPlan": false,
    "purchasable": true
  }
]
```

### `GET /api/v1/payments/subscription`
Return the current subscription and token balance.

Success response `200`:
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

### `POST /api/v1/payments/checkout-sessions`
Create a Mercado Pago checkout link for a paid subscription.

Request body:
```json
{
  "planCode": "BASIC",
  "successUrl": "http://localhost:3000/billing/success",
  "cancelUrl": "http://localhost:3000/billing/cancel"
}
```

Success response `200`:
```json
{
  "url": "https://www.mercadopago.com.pe/subscriptions/checkout"
}
```

Common errors:
- `400` invalid plan or missing request body
- `403` user already has an active paid subscription

### `POST /api/v1/payments/subscription/cancel`
Cancel the current Mercado Pago subscription.

Success response `204` with empty body.

Common errors:
- `400` no active Mercado Pago subscription to cancel

### `POST /api/v1/payments/webhook/mercado-pago`
Public webhook endpoint for Mercado Pago notifications.

Success response `200` with empty body.

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
- Backend persists the user message, session update, and outbox event in one transaction before async delivery continues.
- Token consumption is applied after assistant processing finishes, once the backend knows which assistant route was used.
- Parser-only responses consume up to `1` token; full RAG responses consume up to `3` tokens.
- If fewer tokens remain than the route cost, backend consumes the remaining balance without going negative.
- If async assistant processing fails before a result, no token consumption is applied.
- Default mode (`CHAT_RABBIT_ENABLED=true`): transactional outbox -> RabbitMQ relay -> consumer -> n8n.
- Fallback mode (`CHAT_RABBIT_ENABLED=false`): transactional outbox -> local async dispatch after commit.
- Assistant response is persisted in DB before SSE dispatch is attempted.
- If SSE is disconnected, frontend can recover data from `GET /api/v1/chat/sessions/{sessionId}/messages?size=20`.

Common errors:
- `400` `message` missing/blank
- `400` `sessionId` missing
- `403` forbidden session access
- `403` session belongs to another user
- `409` another message is still processing

### `GET /api/v1/chat/processing-status`
Return whether the authenticated user currently has one chat message waiting for an assistant result.

Success response `200`:
```json
{
  "processing": true,
  "chatSessionId": "uuid",
  "userMessageId": "uuid",
  "status": "PROCESSING",
  "updatedAt": "2026-01-01T00:00:00Z"
}
```

When there is no pending message, `processing` is `false` and the other fields are `null`.

### `POST /api/v1/chat/sessions`
Create a new chat session.

Success response `201`:
```json
{
  "id": "uuid",
  "title": null,
  "createdAt": "2026-04-19T10:00:00Z",
  "updatedAt": "2026-04-19T10:00:00Z"
}
```

### `PATCH /api/v1/chat/sessions/{sessionId}`
Rename a chat session.

Request body:
```json
{
  "title": "Consulta de alimentos"
}
```

Success response `200`:
```json
{
  "id": "uuid",
  "title": "Consulta de alimentos",
  "createdAt": "2026-04-19T10:00:00Z",
  "updatedAt": "2026-04-19T10:01:00Z"
}
```

### `DELETE /api/v1/chat/sessions/{sessionId}`
Delete a chat session and its messages.

Success response `204` with empty body.

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

Query parameters:
- `size`: optional, default `20`, allowed range `1..100`.
- `cursor`: optional. Omit it for the first batch. Use `nextCursor` from the previous response for the next batch.

Success response `200`:
```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Consulta de alimentos",
      "createdAt": "2026-04-19T10:00:00Z",
      "updatedAt": "2026-04-19T10:03:00Z"
    }
  ],
  "nextCursor": 20
}
```

`nextCursor` is a numeric offset. Pass it unchanged as the `cursor` query parameter on the next request. When `nextCursor` is `null`, there are no more results.

### `GET /api/v1/chat/sessions/{sessionId}/messages`
List messages in one session (ordered oldest to newest).

Query parameters:
- `size`: optional, default `20`, allowed range `1..100`.
- `cursor`: optional. Omit it for the first batch. Use `nextCursor` from the previous response for the next batch.

Success response `200`:
```json
{
  "content": [
    {
      "id": "uuid",
      "role": "USER",
      "content": "Hola",
      "errorCode": null,
      "rating": null,
      "createdAt": "2026-04-19T10:00:00Z",
      "citations": []
    },
    {
      "id": "uuid",
      "role": "ASSISTANT",
      "content": "Hola, en que puedo ayudarte?",
      "errorCode": null,
      "rating": 5,
      "createdAt": "2026-04-19T10:00:02Z",
      "receiptStatus": "PUBLISHED",
      "readAt": null,
      "citations": []
    }
  ],
  "nextCursor": null
}
```

Common errors:
- `403` session belongs to another user
- `404` session not found

Failure behavior:
- If upstream n8n fails (for example `404`), backend persists a `SYSTEM` message with a stable `errorCode` and an English fallback message.
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

### `PATCH /api/v1/chat/messages/{messageId}/receipt`
Confirm that an assistant message has been rendered/read by the frontend.

Success response `204` with empty body.

Common errors:
- `400` message is not an assistant message
- `403` message belongs to another user
- `404` message or delivery event not found

## Standard Error Shape

All error responses return:
```json
{
  "type": "validation_error",
  "code": "message_required",
  "message": "Message is required",
  "status": 400,
  "path": "/api/v1/chat/send",
  "timestamp": "2026-04-19T12:00:00.000Z"
}
```

`message` is an English fallback only. Frontend custom copy should be keyed from `code`.

Backend error definitions are owned by their bounded context: shared transport/security errors live in `common`, while auth, chat, and payment codes live in their respective modules.

## Error Code Catalog

Global and security:

| Code | HTTP | Type | Fallback message |
| --- | ---: | --- | --- |
| `unauthorized` | 401 | `authentication_error` | Authentication is required |
| `forbidden` | 403 | `authorization_error` | Access is forbidden |
| `malformed_json` | 400 | `validation_error` | Malformed request body |
| `invalid_request` | 400 | `validation_error` | Request validation failed |
| `max_upload_size_exceeded` | 413 | `validation_error` | File exceeds configured upload size limit |
| `internal_server_error` | 500 | `internal_error` | An unexpected error occurred |

Request validation:

| Code | HTTP | Type | Fallback message |
| --- | ---: | --- | --- |
| `email_required` | 400 | `validation_error` | Email is required |
| `email_invalid` | 400 | `validation_error` | Valid email is required |
| `email_too_long` | 400 | `validation_error` | Email must be at most 255 characters |
| `password_required` | 400 | `validation_error` | Password is required |
| `password_length_invalid` | 400 | `validation_error` | Password length is invalid |
| `name_required` | 400 | `validation_error` | Name is required |
| `name_too_long` | 400 | `validation_error` | Name must be at most 120 characters |
| `phone_required` | 400 | `validation_error` | Phone is required |
| `phone_too_long` | 400 | `validation_error` | Phone must be at most 30 characters |
| `refresh_token_required` | 400 | `validation_error` | Refresh token is required |
| `message_required` | 400 | `validation_error` | Message is required |
| `message_too_long` | 400 | `validation_error` | Message must be at most 4000 characters |
| `session_id_required` | 400 | `validation_error` | Session id is required |
| `session_title_required` | 400 | `validation_error` | Session title is required |
| `session_title_too_long` | 400 | `validation_error` | Session title must be at most 120 characters |
| `rating_required` | 400 | `validation_error` | Rating is required |
| `rating_out_of_range` | 400 | `validation_error` | Rating must be between 1 and 5 |
| `feedback_comment_too_long` | 400 | `validation_error` | Feedback comment must be at most 1000 characters |
| `plan_code_required` | 400 | `validation_error` | Plan code is required |
| `plan_code_too_long` | 400 | `validation_error` | Plan code must be at most 40 characters |
| `plan_code_invalid` | 400 | `validation_error` | Plan code is invalid |
| `success_url_too_long` | 400 | `validation_error` | Success URL must be at most 2048 characters |
| `success_url_invalid` | 400 | `validation_error` | Success URL must be an HTTP URL |
| `cancel_url_too_long` | 400 | `validation_error` | Cancel URL must be at most 2048 characters |
| `cancel_url_invalid` | 400 | `validation_error` | Cancel URL must be an HTTP URL |

Auth:

| Code | HTTP | Type | Fallback message |
| --- | ---: | --- | --- |
| `email_already_exists` | 409 | `conflict_error` | Email already exists |
| `invalid_credentials` | 401 | `authentication_error` | Invalid credentials |
| `invalid_refresh_token` | 401 | `authentication_error` | Invalid refresh token |
| `signup_request_required` | 400 | `validation_error` | Signup request body is required |
| `login_request_required` | 400 | `validation_error` | Login request body is required |

Chat:

| Code | HTTP | Type | Fallback message |
| --- | ---: | --- | --- |
| `chat_session_not_found` | 404 | `resource_error` | Chat session not found |
| `chat_message_not_found` | 404 | `resource_error` | Chat message not found |
| `assistant_delivery_event_not_found` | 404 | `resource_error` | Assistant delivery event not found |
| `message_processing_pending` | 409 | `chat_state_error` | Message processing is already pending |
| `assistant_receipt_pending` | 409 | `chat_state_error` | Assistant receipt confirmation is still pending for this session |
| `personal_data_not_allowed` | 400 | `validation_error` | Personal data is not allowed in chat messages |
| `metadata_only_assistant` | 400 | `validation_error` | Metadata can only be applied to assistant messages |
| `only_assistant_messages_can_be_rated` | 400 | `validation_error` | Only assistant messages can be rated |
| `receipt_only_assistant_messages` | 400 | `validation_error` | Receipt can only be confirmed for assistant messages |
| `cursor_invalid` | 400 | `validation_error` | Cursor query is invalid |
| `upstream_error` | 502 | `upstream_error` | Assistant service failed to prepare a response |
| `upstream_timeout` | 502 | `upstream_error` | Assistant service timed out |
| `upstream_empty_response` | 502 | `upstream_error` | Assistant service returned an empty response |
| `upstream_invalid_response` | 502 | `upstream_error` | Assistant service returned an invalid response |
| `upstream_not_configured` | 502 | `upstream_error` | Assistant service is not configured |
| `upstream_unavailable` | 502 | `upstream_error` | Assistant service is unavailable |
| `upstream_request_invalid` | 502 | `upstream_error` | Assistant service request could not be prepared |
| `agent_validation_failed` | 502 | `upstream_error` | Assistant response validation failed |

Payment:

| Code | HTTP | Type | Fallback message |
| --- | ---: | --- | --- |
| `checkout_request_required` | 400 | `validation_error` | Checkout request body is required |
| `paid_plan_required` | 400 | `validation_error` | Paid plan is required |
| `plan_not_purchasable` | 400 | `validation_error` | Selected plan is not configured for checkout |
| `checkout_plan_already_active` | 403 | `payment_error` | User is already subscribed to the selected plan |
| `checkout_active_gateway_subscription` | 403 | `payment_error` | Cancel the current gateway subscription before changing plans |
| `no_gateway_subscription_to_cancel` | 400 | `payment_error` | No gateway subscription is available to cancel |
| `subscription_not_found` | 404 | `payment_error` | Subscription not found |
| `subscription_inactive` | 403 | `payment_error` | Subscription is not active |
| `insufficient_tokens` | 403 | `payment_error` | Insufficient tokens |
| `webhook_payload_required` | 400 | `payment_error` | Webhook payload is required |
| `payment_webhook_unmatched_user` | 400 | `payment_error` | Payment webhook cannot be matched to a local user |
| `webhook_payload_invalid` | 400 | `payment_error` | Webhook payload is invalid |
| `webhook_user_reference_invalid` | 400 | `payment_error` | Webhook user reference is invalid |
| `webhook_request_id_required` | 400 | `payment_error` | Webhook request id is required |
| `webhook_data_id_required` | 400 | `payment_error` | Webhook data id is required |
| `webhook_signature_invalid` | 400 | `payment_error` | Webhook signature is invalid |
| `webhook_signature_required` | 400 | `payment_error` | Webhook signature is required |
| `webhook_signature_unverifiable` | 400 | `payment_error` | Webhook signature cannot be verified |
| `payment_gateway_unavailable` | 503 | `payment_error` | Payment gateway is unavailable |
| `payment_gateway_empty_response` | 503 | `payment_error` | Payment gateway returned an empty response |
| `payment_gateway_misconfigured` | 503 | `payment_error` | Payment gateway is misconfigured |
| `payment_gateway_payer_email_required` | 503 | `payment_error` | Payment gateway requires a payer email |
| `payment_gateway_checkout_url_missing` | 503 | `payment_error` | Payment gateway did not return a checkout URL |
| `payment_gateway_subscription_id_required` | 503 | `payment_error` | Payment gateway subscription id is required |

Async chat failures:

`assistant_error` SSE payloads and failed `SYSTEM` messages expose the same lowercase `errorCode` values as the chat upstream codes above. Historical rows created before this contract may still contain older uppercase codes or Spanish text.

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

## Frontend Implementation Guide

### 1. Auth and token refresh

- Centralize all HTTP calls in one API client.
- Add `Authorization: Bearer <accessToken>` automatically to protected endpoints.
- On `401`, try exactly one `POST /api/v1/auth/refresh` and replay the original request once.
- If refresh also fails with `401`, clear session state and redirect to login.
- Prevent parallel refresh storms: if many requests fail with `401` at the same time, run one refresh request and queue the others until it finishes.

### 2. Chat send: correct UX and duplicate prevention

- When user presses send, create a local optimistic `USER` message in UI with a temporary client id and state like `sending`.
- Disable repeated submits for the same text while the first `POST /api/v1/chat/send` is in flight.
- If `/chat/send` returns `202`, replace the temporary id with `userMessageId` from backend and mark the message as `processing`.
- Do not auto-retry `/chat/send` blindly after network timeout, browser abort, or unknown connection loss. The request may already have been accepted and would create another queued message if sent again.
- If send result is unknown, show a recoverable banner such as "Connection interrupted. We are checking your conversation status." then call `GET /api/v1/chat/sessions/{sessionId}/messages?size=20` to reconcile.
- After reconciliation:
- If the user message appears in history, keep it and continue waiting for assistant completion.
- If it does not appear, allow the user to send again manually.

### 3. SSE connection strategy

- Open `GET /api/v1/chat/subscribe/{sessionId}` as soon as the session screen is active.
- Reconnect SSE with exponential backoff, for example `1s`, `2s`, `5s`, `10s`, max `30s`.
- On every reconnect, immediately reload `GET /api/v1/chat/sessions/{sessionId}/messages?size=20` before trusting only live events.
- Treat SSE as a real-time delivery channel, not as the source of truth.
- The source of truth for rendering the chat history is always `GET /api/v1/chat/sessions/{sessionId}/messages?size=20`.
- Ignore duplicate SSE payloads if the same persisted message id is already rendered.
- If SSE is unavailable for a prolonged period, continue polling `GET /api/v1/chat/sessions/{sessionId}/messages?size=20` every few seconds while there is at least one message still waiting for assistant completion.

### 4. Recommended chat screen state machine

- `idle`: input enabled, no pending request.
- `sending`: `POST /chat/send` in flight.
- `processing`: backend accepted the message and assistant response is still pending.
- `completed`: assistant `ASSISTANT` message arrived.
- `failed`: backend persisted a `SYSTEM` error message or SSE `assistant_error` was received.
- `reconnecting`: SSE disconnected but chat can still be recovered from history endpoint.

Recommended rendering behavior:
- Show the user message immediately.
- Show a spinner or "Analizando..." placeholder while in `processing`.
- Replace placeholder when an `ASSISTANT` or `SYSTEM` persisted message appears in history.
- Use `GET /api/v1/chat/processing-status` to recover pending state after reloads, tab focus changes, login restoration, or switching sessions.

### 5. History reconciliation logic

Use `GET /api/v1/chat/sessions/{sessionId}/messages?size=20` in these cases:
- Initial session load.
- After browser refresh.
- After SSE reconnect.
- After unknown `/chat/send` result.
- After app returns from offline state.
- Before enabling message rating if the local state may be stale.

Suggested reconciliation rule:
- Merge by persisted `message.id`.
- For `USER` messages, keep the oldest matching persisted item and remove local temporary duplicates.
- For `ASSISTANT` and `SYSTEM` messages, append only if `message.id` is new.

### 6. Connectivity and offline handling

- Detect offline mode with browser connectivity signals, but do not trust them as exact truth.
- If the browser goes offline while user is typing, keep the draft locally.
- If the browser goes offline before `/chat/send` finishes, mark the message as `unknown_delivery` instead of failed.
- When connection returns, reconcile against `GET /api/v1/chat/sessions/{sessionId}/messages?size=20` before allowing resend.
- For regular `GET` endpoints like plans, subscription, sessions, and messages, safe automatic retries with short backoff are acceptable.
- For mutation endpoints like `/chat/send`, `/payments/checkout-sessions`, `/payments/subscription/cancel`, and `/chat/messages/{messageId}/rating`, retry only when the failure is clearly before request dispatch, not after an uncertain transport break.

### 7. Error handling by endpoint type

For `POST /api/v1/chat/send`:
- `400`: show inline validation error.
- `403` from protected chat endpoints: block input when access is forbidden and refresh `GET /api/v1/payments/subscription` if the error came from billing.
- `403` session forbidden: return user to session list.
- Unknown network failure: reconcile message history before offering resend.

For `GET /api/v1/chat/subscribe/{sessionId}`:
- `403` or `404`: stop reconnect loop and navigate away from the session view.
- Transport disconnect: reconnect with backoff and refresh history.

For `GET /api/v1/payments/subscription`:
- Refresh this after assistant success, after checkout success return, and after cancellation, because token balance or plan state may have changed.

### 8. Payments flow recommendations

- Use `GET /api/v1/payments/plans` to build pricing UI and disable plan buttons where `purchasable=false`.
- After `POST /api/v1/payments/checkout-sessions`, redirect immediately to returned `url`.
- When frontend returns from Mercado Pago success page, call `GET /api/v1/payments/subscription` and `GET /api/v1/payments/plans` to refresh plan badges and token quotas.
- After `POST /api/v1/payments/subscription/cancel`, refresh subscription state and update UI to free-plan expectations only after backend confirms.
- Do not assume webhook processing is instantaneous; the frontend must re-fetch subscription state instead of assuming checkout completed immediately.

### 9. Minimal robust frontend flow for chat

1. Create or load session.
2. Load message history.
3. Open SSE.
4. Send message with optimistic local row.
5. If `202`, mark local message as `processing` and refresh subscription summary in background.
6. Wait for SSE, but also reconcile via history on reconnect or uncertainty.
7. When persisted `ASSISTANT` message appears, stop pending UI.
8. When persisted `SYSTEM` message appears, show error state. No chat token is consumed when assistant processing fails before producing a result.

## Why This Architecture Helps Frontend Resilience

- `POST /api/v1/chat/send` only needs the backend to persist the minimum consistent state and accept the work; it does not wait for the full assistant pipeline to finish.
- This reduces the time the user stays blocked on a slow network request and lowers the chance of browser/network timeouts on high-latency connections.
- After acceptance, the frontend can safely move to a `processing` UI state while backend async processing continues through outbox + queue + consumer.
- If the user's connection drops after acceptance, the chat is still recoverable because the source of truth is persisted in the database.
- SSE improves responsiveness, but it is not required for correctness; the frontend can always resynchronize from `GET /api/v1/chat/sessions/{sessionId}/messages?size=20`.
- This makes the UI robust for unstable mobile networks, intermittent Wi-Fi, browser refreshes, and temporary disconnects from SSE.
