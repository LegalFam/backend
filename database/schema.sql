CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens(expires_at);

CREATE TABLE IF NOT EXISTS auth_one_time_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(512) NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX idx_auth_one_time_tokens_user_purpose
    ON auth_one_time_tokens(user_id, purpose, created_at DESC);

CREATE INDEX idx_auth_one_time_tokens_expires_at
    ON auth_one_time_tokens(expires_at);

CREATE TABLE IF NOT EXISTS chat_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_chat_session_user_id_updated_at
    ON chat_session(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS chat_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_session_id UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content TEXT NOT NULL,
    error_code VARCHAR(255) NULL,
    rating INTEGER NULL CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    feedback_comment TEXT NULL,
    feedback_submitted_at TIMESTAMP WITH TIME ZONE NULL,
    confidence_status VARCHAR(32) NULL CHECK (confidence_status IS NULL OR confidence_status IN ('HIGH', 'MEDIUM', 'LOW')),
    confidence_reason TEXT NULL,
    next_steps TEXT NULL,
    specialist_support_recommended BOOLEAN NULL,
    citation_support_status VARCHAR(32) NULL CHECK (citation_support_status IS NULL OR citation_support_status IN ('GOOD', 'WEAK', 'NONE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_chat_message_session_created_at
    ON chat_message(chat_session_id, created_at ASC);

CREATE TABLE IF NOT EXISTS chat_message_processing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_message_id UUID NOT NULL UNIQUE REFERENCES chat_message(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    error_code VARCHAR(255) NULL,
    error_message TEXT NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_chat_message_processing_status_updated_at
    ON chat_message_processing(status, updated_at);

CREATE TABLE IF NOT EXISTS chat_outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    chat_session_id UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'READ')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NULL,
    read_at TIMESTAMP WITH TIME ZONE NULL,
    last_error TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_chat_outbox_event_status_available_at
    ON chat_outbox_event(status, available_at, created_at);

CREATE UNIQUE INDEX idx_chat_outbox_event_aggregate_id
    ON chat_outbox_event(aggregate_id);

CREATE TABLE IF NOT EXISTS citations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id UUID NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    source_title TEXT NOT NULL,
    source_snippet TEXT NOT NULL,
    source_original_snippet TEXT NULL,
    source_url TEXT NOT NULL,
    source_locator TEXT NULL,
    source_breadcrumb TEXT NULL,
    source_locator_kind VARCHAR(32) NULL,
    CONSTRAINT citations_source_locator_kind_check CHECK (source_locator_kind IS NULL OR source_locator_kind IN
        ('exact', 'prefix', 'fuzzy', 'markdown_heading', 'snippet_regex'))
);

CREATE INDEX idx_citations_chat_message_id
    ON citations(chat_message_id, id ASC);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    plan_code VARCHAR(32) NOT NULL CHECK (plan_code IN ('FREE', 'BASIC', 'PREMIUM')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'INCOMPLETE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    provider VARCHAR(32) NOT NULL CHECK (provider IN ('FREE', 'MERCADO_PAGO')),
    gateway_customer_id VARCHAR(255),
    gateway_subscription_id VARCHAR(255),
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    monthly_token_limit INTEGER NOT NULL CHECK (monthly_token_limit >= 0),
    remaining_tokens INTEGER NOT NULL CHECK (remaining_tokens >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_subscriptions_plan_code
    ON subscriptions(plan_code);

CREATE INDEX idx_subscriptions_status
    ON subscriptions(status);

CREATE UNIQUE INDEX idx_subscriptions_gateway_customer_id
    ON subscriptions(gateway_customer_id)
    WHERE gateway_customer_id IS NOT NULL;

CREATE UNIQUE INDEX idx_subscriptions_gateway_subscription_id
    ON subscriptions(gateway_subscription_id)
    WHERE gateway_subscription_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS token_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_message_id UUID NULL REFERENCES chat_message(id) ON DELETE SET NULL,
    type VARCHAR(64) NOT NULL CHECK (type IN ('PERIOD_ALLOCATION', 'CHAT_CONSUMPTION', 'CHAT_REFUND', 'PLAN_CHANGE_ADJUSTMENT')),
    token_delta INTEGER NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_token_transactions_subscription_id
    ON token_transactions(subscription_id);

CREATE INDEX idx_token_transactions_user_id
    ON token_transactions(user_id);

CREATE UNIQUE INDEX idx_token_transactions_chat_message_id_type
    ON token_transactions(chat_message_id, type)
    WHERE chat_message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payment_webhook_events_processed_at
    ON payment_webhook_events(processed_at);
