-- V5: Create expenses table with status workflow CHECK constraint
CREATE TABLE expenses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL REFERENCES organizations(id),
    submitter_id      UUID          NOT NULL REFERENCES users(id),
    manager_id        UUID          REFERENCES users(id),
    amount            DECIMAL(12,2),
    currency          VARCHAR(3),
    category_id       UUID          REFERENCES expense_categories(id),
    merchant_name     VARCHAR(200),
    expense_date      DATE,
    notes             TEXT,
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                      CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    rejection_comment TEXT,
    approved_by_id    UUID          REFERENCES users(id),
    approved_at       TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expenses_tenant ON expenses(tenant_id);
CREATE INDEX idx_expenses_submitter ON expenses(tenant_id, submitter_id);
CREATE INDEX idx_expenses_manager_status ON expenses(tenant_id, manager_id, status);
CREATE INDEX idx_expenses_status ON expenses(tenant_id, status);
CREATE INDEX idx_expenses_category ON expenses(tenant_id, category_id);
