ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS citation_support_status VARCHAR(32) NULL;

ALTER TABLE chat_message
    DROP CONSTRAINT IF EXISTS chat_message_citation_support_status_check;

ALTER TABLE chat_message
    ADD CONSTRAINT chat_message_citation_support_status_check
    CHECK (citation_support_status IS NULL OR citation_support_status IN ('GOOD', 'WEAK', 'NONE'));
