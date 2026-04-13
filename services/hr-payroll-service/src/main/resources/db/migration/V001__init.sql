CREATE SCHEMA IF NOT EXISTS hr;

CREATE TABLE hr.employees (
    employee_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    staff_number        VARCHAR(32) NOT NULL,
    provider_id         VARCHAR(128),
    cpid                VARCHAR(128),
    given_name          VARCHAR(128) NOT NULL,
    family_name         VARCHAR(128) NOT NULL,
    date_of_birth       DATE,
    sex                 VARCHAR(8),
    national_id_hash    VARCHAR(255),
    phone               VARCHAR(32),
    email               VARCHAR(128),
    facility_id         UUID,
    department_id       VARCHAR(128),
    job_title           VARCHAR(128),
    cadre               VARCHAR(64),
    employment_type     VARCHAR(32) NOT NULL,
    employment_status   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    hire_date           DATE,
    termination_date    DATE,
    bank_account        VARCHAR(128),
    bank_name           VARCHAR(64),
    pension_number      VARCHAR(64),
    tax_number          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_staff_number UNIQUE (tenant_id, staff_number),
    CONSTRAINT chk_hr_emp_type CHECK (employment_type IN ('PERMANENT', 'CONTRACT', 'LOCUM', 'VOLUNTEER')),
    CONSTRAINT chk_hr_emp_status CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'TERMINATED'))
);

CREATE INDEX idx_hr_employees_tenant ON hr.employees (tenant_id);

CREATE TABLE hr.contracts (
    contract_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID NOT NULL REFERENCES hr.employees(employee_id),
    contract_type   VARCHAR(32) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE,
    basic_salary    DECIMAL(14,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    allowances_json JSONB,
    deductions_json JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE hr.leave_types (
    leave_type_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    name                VARCHAR(64) NOT NULL,
    annual_entitlement  INT NOT NULL DEFAULT 0,
    carry_over_max      INT NOT NULL DEFAULT 0,
    requires_approval   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE hr.leave_requests (
    request_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID NOT NULL REFERENCES hr.employees(employee_id),
    leave_type_id   UUID NOT NULL REFERENCES hr.leave_types(leave_type_id),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    days            INT NOT NULL,
    reason          TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approved_by     VARCHAR(128),
    approved_at     TIMESTAMPTZ,
    CONSTRAINT chk_hr_leave_req_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE TABLE hr.leave_balances (
    balance_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID NOT NULL REFERENCES hr.employees(employee_id),
    leave_type_id   UUID NOT NULL REFERENCES hr.leave_types(leave_type_id),
    fiscal_year     INT NOT NULL,
    entitlement     INT NOT NULL DEFAULT 0,
    used            INT NOT NULL DEFAULT 0,
    carried_over    INT NOT NULL DEFAULT 0,
    available       INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_hr_leave_balance UNIQUE (employee_id, leave_type_id, fiscal_year)
);

CREATE TABLE hr.attendance_records (
    record_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID NOT NULL REFERENCES hr.employees(employee_id),
    facility_id     UUID,
    shift_date      DATE NOT NULL,
    clock_in        TIMESTAMPTZ,
    clock_out       TIMESTAMPTZ,
    hours_worked    DECIMAL(5,2),
    overtime_hours  DECIMAL(5,2),
    status          VARCHAR(16) NOT NULL,
    CONSTRAINT chk_hr_att_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'ON_LEAVE', 'HOLIDAY'))
);

CREATE TABLE hr.payroll_runs (
    run_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    period_month        INT NOT NULL,
    period_year         INT NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    total_gross         DECIMAL(14,2) DEFAULT 0,
    total_deductions    DECIMAL(14,2) DEFAULT 0,
    total_net           DECIMAL(14,2) DEFAULT 0,
    approved_by         VARCHAR(128),
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_payroll_period UNIQUE (tenant_id, period_year, period_month),
    CONSTRAINT chk_hr_payroll_status CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'PAID', 'REVERSED'))
);

CREATE TABLE hr.payslips (
    payslip_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id              UUID NOT NULL REFERENCES hr.payroll_runs(run_id) ON DELETE CASCADE,
    employee_id         UUID NOT NULL REFERENCES hr.employees(employee_id),
    basic_salary        DECIMAL(14,2) NOT NULL,
    allowances_json     JSONB,
    deductions_json     JSONB,
    gross_pay           DECIMAL(14,2) NOT NULL,
    tax                 DECIMAL(14,2) DEFAULT 0,
    pension             DECIMAL(14,2) DEFAULT 0,
    other_deductions    DECIMAL(14,2) DEFAULT 0,
    net_pay             DECIMAL(14,2) NOT NULL,
    payment_method      VARCHAR(32),
    payment_ref         VARCHAR(128)
);

CREATE TABLE hr.deduction_types (
    type_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    name                VARCHAR(64) NOT NULL,
    category            VARCHAR(32) NOT NULL,
    calculation_method  VARCHAR(16) NOT NULL,
    rate                DECIMAL(10,4),
    is_statutory        BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_hr_ded_cat CHECK (category IN ('TAX', 'PENSION', 'INSURANCE', 'UNION', 'LOAN', 'OTHER')),
    CONSTRAINT chk_hr_ded_calc CHECK (calculation_method IN ('PERCENTAGE', 'FIXED'))
);

CREATE TABLE hr.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'hr-payroll-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_hr_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_hr_outbox_unpublished ON hr.event_outbox (created_at) WHERE published_at IS NULL;
