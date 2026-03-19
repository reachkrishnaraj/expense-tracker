-- V8: Seed demo data for development and testing
-- Uses DO blocks with variables to maintain FK relationships via hardcoded UUIDs.

DO $$
DECLARE
    -- Organization IDs
    v_acme_id     UUID := 'a0000000-0000-0000-0000-000000000001';
    v_globex_id   UUID := 'b0000000-0000-0000-0000-000000000002';

    -- Acme Corp User IDs
    v_acme_admin   UUID := 'b0000000-0000-0000-0000-000000000001';
    v_acme_manager UUID := 'b0000000-0000-0000-0000-000000000002';
    v_acme_john    UUID := 'b0000000-0000-0000-0000-000000000003';
    v_acme_jane    UUID := 'b0000000-0000-0000-0000-000000000004';

    -- Globex Inc User IDs
    v_globex_admin   UUID := 'b0000000-0000-0000-0000-000000000005';
    v_globex_manager UUID := 'b0000000-0000-0000-0000-000000000006';
    v_globex_bob     UUID := 'b0000000-0000-0000-0000-000000000007';

    -- BCrypt hash of "Password1" (cost 12)
    v_password_hash VARCHAR := '$2a$12$6e/Bgz11uyWXTubDJulLHuKbvq9vO2N8c5hf/EnhahvWpcsPy5Mh6';

    -- Acme Category IDs
    v_acme_cat_travel   UUID := 'c0000000-0000-0000-0000-000000000001';
    v_acme_cat_meals    UUID := 'c0000000-0000-0000-0000-000000000002';
    v_acme_cat_office   UUID := 'c0000000-0000-0000-0000-000000000003';
    v_acme_cat_software UUID := 'c0000000-0000-0000-0000-000000000004';
    v_acme_cat_equip    UUID := 'c0000000-0000-0000-0000-000000000005';
    v_acme_cat_other    UUID := 'c0000000-0000-0000-0000-000000000006';

    -- Globex Category IDs
    v_globex_cat_travel   UUID := 'c0000000-0000-0000-0000-000000000011';
    v_globex_cat_meals    UUID := 'c0000000-0000-0000-0000-000000000012';
    v_globex_cat_office   UUID := 'c0000000-0000-0000-0000-000000000013';
    v_globex_cat_software UUID := 'c0000000-0000-0000-0000-000000000014';
    v_globex_cat_equip    UUID := 'c0000000-0000-0000-0000-000000000015';
    v_globex_cat_other    UUID := 'c0000000-0000-0000-0000-000000000016';

    -- Expense IDs (Acme Corp)
    v_expense_1  UUID := 'd0000000-0000-0000-0000-000000000001';  -- DRAFT  John  Meals
    v_expense_2  UUID := 'd0000000-0000-0000-0000-000000000002';  -- DRAFT  Jane  Equipment
    v_expense_3  UUID := 'd0000000-0000-0000-0000-000000000003';  -- SUBMITTED John Travel
    v_expense_4  UUID := 'd0000000-0000-0000-0000-000000000004';  -- SUBMITTED John Meals
    v_expense_5  UUID := 'd0000000-0000-0000-0000-000000000005';  -- SUBMITTED Jane Software
    v_expense_6  UUID := 'd0000000-0000-0000-0000-000000000006';  -- APPROVED  John Travel
    v_expense_7  UUID := 'd0000000-0000-0000-0000-000000000007';  -- APPROVED  Jane Meals
    v_expense_8  UUID := 'd0000000-0000-0000-0000-000000000008';  -- APPROVED  John Office
    v_expense_9  UUID := 'd0000000-0000-0000-0000-000000000009';  -- REJECTED  Jane Equipment
    v_expense_10 UUID := 'd0000000-0000-0000-0000-000000000010'; -- CANCELLED John Other
BEGIN

    -- =============================
    -- Organizations
    -- =============================
    INSERT INTO organizations (id, name, slug, currency, is_active) VALUES
        (v_acme_id,   'Acme Corp',  'acme-corp',  'USD', TRUE),
        (v_globex_id, 'Globex Inc', 'globex-inc', 'USD', TRUE);

    -- =============================
    -- Users - Acme Corp
    -- =============================
    -- Admin (no manager)
    INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role, manager_id, is_active) VALUES
        (v_acme_admin, v_acme_id, 'admin@acme.com', v_password_hash, 'Alice', 'Admin', 'ADMIN', NULL, TRUE);

    -- Manager (no manager)
    INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role, manager_id, is_active) VALUES
        (v_acme_manager, v_acme_id, 'manager@acme.com', v_password_hash, 'Mike', 'Manager', 'MANAGER', NULL, TRUE);

    -- Employees (manager = Mike Manager)
    INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role, manager_id, is_active) VALUES
        (v_acme_john, v_acme_id, 'john@acme.com', v_password_hash, 'John', 'Doe', 'EMPLOYEE', v_acme_manager, TRUE),
        (v_acme_jane, v_acme_id, 'jane@acme.com', v_password_hash, 'Jane', 'Smith', 'EMPLOYEE', v_acme_manager, TRUE);

    -- =============================
    -- Users - Globex Inc
    -- =============================
    INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role, manager_id, is_active) VALUES
        (v_globex_admin,   v_globex_id, 'admin@globex.com',   v_password_hash, 'Gary',  'Admin',   'ADMIN',    NULL, TRUE),
        (v_globex_manager, v_globex_id, 'manager@globex.com', v_password_hash, 'Sarah', 'Manager', 'MANAGER',  NULL, TRUE),
        (v_globex_bob,     v_globex_id, 'bob@globex.com',     v_password_hash, 'Bob',   'Wilson',  'EMPLOYEE', v_globex_manager, TRUE);

    -- =============================
    -- Expense Categories - Acme Corp
    -- =============================
    INSERT INTO expense_categories (id, tenant_id, name) VALUES
        (v_acme_cat_travel,   v_acme_id, 'Travel'),
        (v_acme_cat_meals,    v_acme_id, 'Meals'),
        (v_acme_cat_office,   v_acme_id, 'Office Supplies'),
        (v_acme_cat_software, v_acme_id, 'Software'),
        (v_acme_cat_equip,    v_acme_id, 'Equipment'),
        (v_acme_cat_other,    v_acme_id, 'Other');

    -- =============================
    -- Expense Categories - Globex Inc
    -- =============================
    INSERT INTO expense_categories (id, tenant_id, name) VALUES
        (v_globex_cat_travel,   v_globex_id, 'Travel'),
        (v_globex_cat_meals,    v_globex_id, 'Meals'),
        (v_globex_cat_office,   v_globex_id, 'Office Supplies'),
        (v_globex_cat_software, v_globex_id, 'Software'),
        (v_globex_cat_equip,    v_globex_id, 'Equipment'),
        (v_globex_cat_other,    v_globex_id, 'Other');

    -- =============================
    -- Demo Expenses - Acme Corp
    -- =============================

    -- DRAFT expenses (2)
    INSERT INTO expenses (id, tenant_id, submitter_id, manager_id, amount, currency, category_id, merchant_name, expense_date, notes, status) VALUES
        (v_expense_1, v_acme_id, v_acme_john, v_acme_manager, 85.00,  'USD', v_acme_cat_meals, 'Nobu Restaurant',   '2026-03-10', 'Client dinner - drafting',  'DRAFT'),
        (v_expense_2, v_acme_id, v_acme_jane, v_acme_manager, 150.00, 'USD', v_acme_cat_equip, 'Amazon',            '2026-03-12', 'Keyboard request',          'DRAFT');

    -- SUBMITTED expenses (3) - pending approval
    INSERT INTO expenses (id, tenant_id, submitter_id, manager_id, amount, currency, category_id, merchant_name, expense_date, notes, status) VALUES
        (v_expense_3, v_acme_id, v_acme_john, v_acme_manager, 450.00, 'USD', v_acme_cat_travel,   'Delta Airlines',    '2026-02-20', 'NYC flight',       'SUBMITTED'),
        (v_expense_4, v_acme_id, v_acme_john, v_acme_manager, 32.00,  'USD', v_acme_cat_meals,    'Panera Bread',      '2026-03-05', 'Team lunch',       'SUBMITTED'),
        (v_expense_5, v_acme_id, v_acme_jane, v_acme_manager, 299.00, 'USD', v_acme_cat_software, 'Figma',             '2026-03-01', 'Figma license',    'SUBMITTED');

    -- APPROVED expenses (3)
    INSERT INTO expenses (id, tenant_id, submitter_id, manager_id, amount, currency, category_id, merchant_name, expense_date, notes, status, approved_by_id, approved_at) VALUES
        (v_expense_6, v_acme_id, v_acme_john, v_acme_manager, 1200.00, 'USD', v_acme_cat_travel, 'United Airlines',   '2026-01-15', 'Chicago conference',     'APPROVED', v_acme_manager, '2026-01-20 10:30:00'),
        (v_expense_7, v_acme_id, v_acme_jane, v_acme_manager, 45.00,   'USD', v_acme_cat_meals,  'Sweetgreen',        '2026-02-10', 'Working lunch',          'APPROVED', v_acme_manager, '2026-02-12 14:00:00'),
        (v_expense_8, v_acme_id, v_acme_john, v_acme_manager, 89.00,   'USD', v_acme_cat_office, 'Autonomous',        '2026-02-05', 'Monitor stand',          'APPROVED', v_acme_manager, '2026-02-08 11:15:00');

    -- REJECTED expense (1)
    INSERT INTO expenses (id, tenant_id, submitter_id, manager_id, amount, currency, category_id, merchant_name, expense_date, notes, status, rejection_comment) VALUES
        (v_expense_9, v_acme_id, v_acme_jane, v_acme_manager, 5000.00, 'USD', v_acme_cat_equip, 'Fully',  '2026-02-25', 'Standing desk', 'REJECTED', 'Exceeds budget - please get manager pre-approval for items over $1000');

    -- CANCELLED expense (1)
    INSERT INTO expenses (id, tenant_id, submitter_id, manager_id, amount, currency, category_id, merchant_name, expense_date, notes, status) VALUES
        (v_expense_10, v_acme_id, v_acme_john, v_acme_manager, 75.00, 'USD', v_acme_cat_other, 'Best Buy', '2026-01-28', 'USB hub - found one in supply closet', 'CANCELLED');

    -- =============================
    -- Audit Log Entries
    -- =============================

    -- Expense 1 (DRAFT) - created
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_1, 'CREATED', v_acme_john, NULL, 'DRAFT', '2026-03-10 09:00:00');

    -- Expense 2 (DRAFT) - created
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_2, 'CREATED', v_acme_jane, NULL, 'DRAFT', '2026-03-12 08:30:00');

    -- Expense 3 (SUBMITTED) - created then submitted
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_3, 'CREATED',   v_acme_john, NULL,    'DRAFT',     '2026-02-18 10:00:00'),
        (v_expense_3, 'SUBMITTED', v_acme_john, 'DRAFT', 'SUBMITTED', '2026-02-20 09:30:00');

    -- Expense 4 (SUBMITTED) - created then submitted
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_4, 'CREATED',   v_acme_john, NULL,    'DRAFT',     '2026-03-05 12:00:00'),
        (v_expense_4, 'SUBMITTED', v_acme_john, 'DRAFT', 'SUBMITTED', '2026-03-05 12:10:00');

    -- Expense 5 (SUBMITTED) - created then submitted
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_5, 'CREATED',   v_acme_jane, NULL,    'DRAFT',     '2026-02-28 14:00:00'),
        (v_expense_5, 'SUBMITTED', v_acme_jane, 'DRAFT', 'SUBMITTED', '2026-03-01 10:15:00');

    -- Expense 6 (APPROVED) - created, submitted, approved
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_6, 'CREATED',   v_acme_john,    NULL,        'DRAFT',     '2026-01-12 09:00:00'),
        (v_expense_6, 'SUBMITTED', v_acme_john,    'DRAFT',     'SUBMITTED', '2026-01-15 08:45:00'),
        (v_expense_6, 'APPROVED',  v_acme_manager, 'SUBMITTED', 'APPROVED',  '2026-01-20 10:30:00');

    -- Expense 7 (APPROVED) - created, submitted, approved
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_7, 'CREATED',   v_acme_jane,    NULL,        'DRAFT',     '2026-02-08 11:00:00'),
        (v_expense_7, 'SUBMITTED', v_acme_jane,    'DRAFT',     'SUBMITTED', '2026-02-10 09:30:00'),
        (v_expense_7, 'APPROVED',  v_acme_manager, 'SUBMITTED', 'APPROVED',  '2026-02-12 14:00:00');

    -- Expense 8 (APPROVED) - created, submitted, approved
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_8, 'CREATED',   v_acme_john,    NULL,        'DRAFT',     '2026-02-03 10:00:00'),
        (v_expense_8, 'SUBMITTED', v_acme_john,    'DRAFT',     'SUBMITTED', '2026-02-05 11:00:00'),
        (v_expense_8, 'APPROVED',  v_acme_manager, 'SUBMITTED', 'APPROVED',  '2026-02-08 11:15:00');

    -- Expense 9 (REJECTED) - created, submitted, rejected
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, comment, old_status, new_status, created_at) VALUES
        (v_expense_9, 'CREATED',   v_acme_jane,    NULL, NULL,        'DRAFT',     '2026-02-22 10:00:00'),
        (v_expense_9, 'SUBMITTED', v_acme_jane,    NULL, 'DRAFT',     'SUBMITTED', '2026-02-25 10:15:00'),
        (v_expense_9, 'REJECTED',  v_acme_manager, 'Exceeds budget - please get manager pre-approval for items over $1000', 'SUBMITTED', 'REJECTED', '2026-02-27 09:00:00');

    -- Expense 10 (CANCELLED) - created, submitted, cancelled
    INSERT INTO expense_audit_log (expense_id, action, performed_by_id, old_status, new_status, created_at) VALUES
        (v_expense_10, 'CREATED',   v_acme_john, NULL,        'DRAFT',     '2026-01-25 14:00:00'),
        (v_expense_10, 'SUBMITTED', v_acme_john, 'DRAFT',     'SUBMITTED', '2026-01-28 09:00:00'),
        (v_expense_10, 'CANCELLED', v_acme_john, 'SUBMITTED', 'CANCELLED', '2026-01-29 16:30:00');

END $$;
