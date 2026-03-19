-- V7: Create expense_audit_log table (append-only)
CREATE TABLE expense_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id      UUID        NOT NULL REFERENCES expenses(id),
    action          VARCHAR(20) NOT NULL
                    CHECK (action IN ('CREATED','SUBMITTED','APPROVED','REJECTED',
                                      'RESUBMITTED','CANCELLED','REASSIGNED')),
    performed_by_id UUID        NOT NULL REFERENCES users(id),
    comment         TEXT,
    old_status      VARCHAR(20),
    new_status      VARCHAR(20),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_expense ON expense_audit_log(expense_id);
CREATE INDEX idx_audit_created ON expense_audit_log(created_at);
