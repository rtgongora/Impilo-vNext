-- =============================================
-- Service : msika-flow-service (marketplace transaction plane)
-- Database: msika_flow
-- Flyway  : V006__marketplace_rfo.sql
-- Prefix  : mf_
--
-- Wave OF-A marketplace spine (OF-B4/OF-B5/OF-B6/OF-B11):
-- the net-new regulated request-for-offer layer of Vol II §11.
--
-- Naming note: tables use the repo's US-spelling convention
-- (mf_fulfillment_*). Spec logical names (Vol II §11.1) are
-- mf_marketplace_requests, mf_marketplace_invitations,
-- mf_fulfilment_offers, mf_fulfilment_offer_lines, mf_selections.
--
-- PII doctrine (§11.8, binding): mf_marketplace_requests.published_lines_json
-- is the exact PII-minimised snapshot broadcast to competing vendors —
-- ZIBO/ATC-coded lines + quantities + capability/urgency flags ONLY.
-- No patient name, no CPID/HID, no diagnosis, no prescriber identity,
-- no address/coordinates. coarse_zone is a coarse ndila zone id only.
-- =============================================

CREATE TABLE mf_marketplace_requests (
    request_id                VARCHAR(26)   PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    oros_order_id             VARCHAR(64)   NOT NULL,
    oros_order_version_id     VARCHAR(64)   NOT NULL,   -- the immutable version pin (OF-B1)
    profile                   VARCHAR(20)   NOT NULL,   -- MEDICATION | DIAGNOSTICS | SERVICE
    publication_mode          VARCHAR(32)   NOT NULL,   -- §11.2 enum
    status                    VARCHAR(24)   NOT NULL,   -- §9.3 machine
    controlled                BOOLEAN       NOT NULL DEFAULT FALSE,
    coarse_zone               VARCHAR(64),
    urgency                   VARCHAR(32),
    offer_window_ends_at      TIMESTAMPTZ,
    selection_window_ends_at  TIMESTAMPTZ,
    published_lines_json      JSONB         NOT NULL,
    invited_vendor_ids        JSONB,
    prescription_token        VARCHAR(512),             -- opaque §13.2 token; never published
    requested_by              VARCHAR(128),
    cancel_reason             VARCHAR(255),
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ
);

CREATE INDEX idx_mf_mreq_tenant_status ON mf_marketplace_requests(tenant_id, status);
CREATE INDEX idx_mf_mreq_offer_window  ON mf_marketplace_requests(status, offer_window_ends_at);
CREATE INDEX idx_mf_mreq_sel_window    ON mf_marketplace_requests(status, selection_window_ends_at);
CREATE INDEX idx_mf_mreq_oros_order    ON mf_marketplace_requests(oros_order_id);

CREATE TABLE mf_marketplace_invitations (
    invitation_id             VARCHAR(26)   PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    request_id                VARCHAR(26)   NOT NULL REFERENCES mf_marketplace_requests(request_id),
    vendor_id                 VARCHAR(26)   NOT NULL,
    status                    VARCHAR(16)   NOT NULL,   -- SENT | VIEWED | DECLINED | OFFERED | EXPIRED
    eligibility_snapshot_json JSONB,                    -- §11.3 six-precondition evaluation at invitation
    sent_at                   TIMESTAMPTZ,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ
);

CREATE INDEX idx_mf_minv_request ON mf_marketplace_invitations(request_id);
CREATE INDEX idx_mf_minv_vendor  ON mf_marketplace_invitations(tenant_id, vendor_id);

CREATE TABLE mf_fulfillment_offers (
    offer_id                  VARCHAR(26)   PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    request_id                VARCHAR(26)   NOT NULL REFERENCES mf_marketplace_requests(request_id),
    invitation_id             VARCHAR(26)   NOT NULL REFERENCES mf_marketplace_invitations(invitation_id),
    vendor_id                 VARCHAR(26)   NOT NULL,
    status                    VARCHAR(24)   NOT NULL,   -- §9.4 machine
    status_reason             VARCHAR(64),              -- RC-coded terminal reason
    price_total               NUMERIC(14,2) NOT NULL,
    currency                  VARCHAR(8)    NOT NULL,
    fulfillment_window_hours  INTEGER,
    ttl_expires_at            TIMESTAMPTZ,              -- ACTIVE: vendor promise; SELECTED+: = DURA reservation TTL
    eligibility_snapshot_json JSONB,
    submitted_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ
);

CREATE INDEX idx_mf_offer_request ON mf_fulfillment_offers(request_id, status);
CREATE INDEX idx_mf_offer_ttl     ON mf_fulfillment_offers(status, ttl_expires_at);
CREATE INDEX idx_mf_offer_vendor  ON mf_fulfillment_offers(tenant_id, vendor_id);

CREATE TABLE mf_fulfillment_offer_lines (
    offer_line_id             VARCHAR(26)   PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    offer_id                  VARCHAR(26)   NOT NULL REFERENCES mf_fulfillment_offers(offer_id),
    request_line_ref          VARCHAR(64)   NOT NULL,
    item_code                 VARCHAR(64)   NOT NULL,
    quantity                  INTEGER       NOT NULL,
    unit_price                NUMERIC(14,2),
    stock_grade               VARCHAR(16)   NOT NULL,   -- VERIFIED | REPORTED (§8E)
    substitution_proposed     BOOLEAN       NOT NULL DEFAULT FALSE,
    dura_reservation_ref      VARCHAR(64),              -- DURA inv_stock_reservations UUID; reference, never a second truth
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_mf_offer_line_offer ON mf_fulfillment_offer_lines(offer_id);
CREATE INDEX idx_mf_offer_line_dura  ON mf_fulfillment_offer_lines(dura_reservation_ref);

CREATE TABLE mf_selections (
    selection_id              VARCHAR(26)   PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    request_id                VARCHAR(26)   NOT NULL REFERENCES mf_marketplace_requests(request_id),
    offer_id                  VARCHAR(26)   NOT NULL REFERENCES mf_fulfillment_offers(offer_id),
    status                    VARCHAR(16)   NOT NULL,   -- SELECTED | REVALIDATING | COMMITTED | FAILED | CANCELLED
    idempotency_key           VARCHAR(128)  NOT NULL,
    prescription_claim_id     VARCHAR(64),
    failure_code              VARCHAR(64),
    step_log_json             JSONB,                    -- §8.9 twelve-step commitment log
    dispatch_ref              VARCHAR(64),
    committed_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ
);

-- RC-1: idempotent retries — one selection per (tenant, key).
CREATE UNIQUE INDEX uq_mf_selection_idem ON mf_selections(tenant_id, idempotency_key);

-- RC-1: at most one live (non-FAILED/CANCELLED) selection per request.
-- Partial index (Postgres); code re-enforces the same invariant for H2 test runs.
CREATE UNIQUE INDEX uq_mf_selection_single_active
    ON mf_selections(request_id)
    WHERE status NOT IN ('FAILED', 'CANCELLED');

CREATE INDEX idx_mf_selection_request ON mf_selections(request_id);
