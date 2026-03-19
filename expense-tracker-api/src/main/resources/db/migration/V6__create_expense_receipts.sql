-- V6: Create expense_receipts table for file attachments
CREATE TABLE expense_receipts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id   UUID         NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT    NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipts_expense ON expense_receipts(expense_id);
