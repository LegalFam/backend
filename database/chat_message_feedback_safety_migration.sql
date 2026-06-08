ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS feedback_comment TEXT NULL;

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS feedback_submitted_at TIMESTAMP WITH TIME ZONE NULL;

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS confidence_status VARCHAR(32) NULL;

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS confidence_reason TEXT NULL;

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS next_steps TEXT NULL;

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS specialist_support_recommended BOOLEAN NULL;

ALTER TABLE chat_message
    DROP CONSTRAINT IF EXISTS chat_message_confidence_status_check;

ALTER TABLE chat_message
    ADD CONSTRAINT chat_message_confidence_status_check
        CHECK (confidence_status IS NULL OR confidence_status IN ('HIGH', 'MEDIUM', 'LOW'));
