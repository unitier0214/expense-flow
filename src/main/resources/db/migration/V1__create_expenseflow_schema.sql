CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT fk_users_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_users_role
        CHECK (role IN ('EMPLOYEE', 'APPROVER'))
);

CREATE TABLE expense_requests (
    id BIGSERIAL PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    category VARCHAR(30) NOT NULL,
    expense_date DATE NOT NULL,
    amount NUMERIC(12, 0) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    CONSTRAINT fk_expense_requests_applicant
        FOREIGN KEY (applicant_id) REFERENCES users (id),
    CONSTRAINT fk_expense_requests_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_expense_requests_category
        CHECK (category IN ('TRANSPORT', 'SUPPLIES', 'OTHER')),
    CONSTRAINT ck_expense_requests_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED')),
    CONSTRAINT ck_expense_requests_amount
        CHECK (amount >= 1 AND amount <= 1000000 AND amount = trunc(amount))
);

CREATE TABLE expense_events (
    id BIGSERIAL PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    comment VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_events_expense
        FOREIGN KEY (expense_id) REFERENCES expense_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_events_actor
        FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT ck_expense_events_action
        CHECK (action IN ('CREATE', 'UPDATE', 'SUBMIT', 'APPROVE', 'RETURN')),
    CONSTRAINT ck_expense_events_from_status
        CHECK (from_status IS NULL OR from_status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED')),
    CONSTRAINT ck_expense_events_to_status
        CHECK (to_status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED'))
);

CREATE INDEX ix_expense_requests_applicant_updated
    ON expense_requests (applicant_id, updated_at DESC, id DESC);

CREATE INDEX ix_expense_requests_department_status_submitted
    ON expense_requests (department_id, status, submitted_at ASC, id ASC);

CREATE INDEX ix_expense_events_expense_occurred
    ON expense_events (expense_id, occurred_at ASC, id ASC);
