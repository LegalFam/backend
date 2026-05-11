ALTER TABLE chat_outbox_event
    DROP CONSTRAINT IF EXISTS chat_outbox_event_status_check;

ALTER TABLE chat_outbox_event
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMP WITH TIME ZONE NULL;

ALTER TABLE chat_outbox_event
    DROP COLUMN IF EXISTS expires_at;

ALTER TABLE chat_outbox_event
    ADD CONSTRAINT chat_outbox_event_status_check
        CHECK (status IN ('PENDING', 'PUBLISHED', 'READ'));

DROP INDEX IF EXISTS idx_chat_outbox_event_aggregate_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_outbox_event_aggregate_id
    ON chat_outbox_event(aggregate_id);

UPDATE chat_outbox_event
SET status = 'PENDING'
WHERE status NOT IN ('PENDING', 'PUBLISHED', 'READ');
