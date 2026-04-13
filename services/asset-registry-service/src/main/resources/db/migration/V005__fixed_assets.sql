-- Fixed asset extension for depreciation, GL mapping, and disposal.

CREATE TABLE asset_fixed_details (
    id                      BIGSERIAL PRIMARY KEY,
    asset_id                UUID NOT NULL REFERENCES asr_assets(asset_id) ON DELETE CASCADE,
    acquisition_date        DATE,
    acquisition_cost        DECIMAL(14,2),
    useful_life_months      INT,
    salvage_value           DECIMAL(14,2),
    depreciation_method     VARCHAR(16) NOT NULL DEFAULT 'STRAIGHT_LINE',
    accumulated_depreciation DECIMAL(14,2) NOT NULL DEFAULT 0,
    net_book_value          DECIMAL(14,2),
    gl_asset_account         VARCHAR(20),
    gl_depreciation_account  VARCHAR(20),
    gl_expense_account       VARCHAR(20),
    disposal_date             DATE,
    disposal_amount           DECIMAL(14,2),
    disposal_reason         TEXT,
    CONSTRAINT chk_asr_dep_method CHECK (depreciation_method IN (
        'STRAIGHT_LINE', 'DECLINING_BALANCE', 'UNITS_OF_PRODUCTION'))
);

CREATE UNIQUE INDEX uq_asr_fixed_asset ON asset_fixed_details (asset_id);

CREATE TABLE asset_depreciation_schedule (
    id                      BIGSERIAL PRIMARY KEY,
    asset_id                UUID NOT NULL REFERENCES asr_assets(asset_id) ON DELETE CASCADE,
    period_date             DATE NOT NULL,
    depreciation_amount     DECIMAL(14,2) NOT NULL,
    accumulated             DECIMAL(14,2) NOT NULL,
    net_book_value          DECIMAL(14,2) NOT NULL,
    posted_to_gl            BOOLEAN NOT NULL DEFAULT FALSE,
    gl_entry_ref            VARCHAR(128)
);

CREATE INDEX idx_asr_dep_sched_asset ON asset_depreciation_schedule (asset_id);
