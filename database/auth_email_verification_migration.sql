-- Email verification on signup + password reset support.
-- Existing accounts are grandfathered in as verified: the column is created with
-- DEFAULT TRUE (backfilling every existing row) and then flipped to DEFAULT FALSE
-- so every account created from now on starts unverified.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE users
    ALTER COLUMN email_verified SET DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE NULL;

UPDATE users
SET email_verified_at = now()
WHERE email_verified = TRUE AND email_verified_at IS NULL;

CREATE TABLE IF NOT EXISTS auth_one_time_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(512) NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_one_time_tokens_user_purpose
    ON auth_one_time_tokens(user_id, purpose, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_one_time_tokens_expires_at
    ON auth_one_time_tokens(expires_at);
