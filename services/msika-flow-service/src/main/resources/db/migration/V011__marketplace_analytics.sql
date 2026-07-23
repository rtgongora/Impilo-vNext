-- =============================================
-- Service : msika-flow-service (marketplace transaction plane)
-- Database: msika_flow
-- Flyway  : V011__marketplace_analytics.sql
-- Prefix  : mf_
--
-- Wave OF-D — OF-B29 anomaly + fairness half (Vol II §13.3/§13.7/§21).
--
-- Architecture decision (documented, binding for this lane): monitors run as
-- SCHEDULED SWEEPS inside msika-flow over its OWN tables + consumed-event
-- projections (mf_reservations rides inventory.reservation.status_changed).
-- No separate analytics service: msika-flow owns the marketplace transaction
-- truth these monitors read, sweeps are deterministic and testable, and a
-- second service would duplicate read models for zero new truth.
-- analytics-pipeline-service integration is a FUTURE SEAM: it subscribes to
-- msika.flow.anomaly.detected.v1 (emitted per finding, coded + PII-free)
-- rather than reading these tables.
--
-- PII doctrine: findings carry CODES, ids and counts only — no names, no
-- addresses, no clinical narrative, no actor identifiers (requester identity
-- is folded into an opaque hash where a per-patient pattern key is needed).
-- =============================================

-- Accountable anomaly/fairness findings (§13.7 "findings route to
-- marketplace-operations worklists" — not fire-and-forget logs).
CREATE TABLE mf_anomaly_findings (
    finding_id       VARCHAR(26)  PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    finding_type     VARCHAR(48)  NOT NULL,  -- coded AnomalyFindingType enum
    severity         VARCHAR(16)  NOT NULL,  -- INFO | WARN | HIGH
    subject_type     VARCHAR(32)  NOT NULL,  -- SELECTION | RESERVATION | VENDOR | ZONE | REQUEST
    subject_ref      VARCHAR(128) NOT NULL,  -- id of the flagged subject
    -- One finding per (tenant, dedupe_key): sweeps re-run without re-flagging.
    dedupe_key       VARCHAR(160) NOT NULL,
    window_start     TIMESTAMPTZ,            -- observation window (aggregate findings)
    window_end       TIMESTAMPTZ,
    details_json     JSONB,                  -- coded evidence: ids, counts, thresholds
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN',  -- OPEN | REVIEWED | DISMISSED
    reviewed_by      VARCHAR(128),           -- accountable reviewer (actor id)
    reviewed_at      TIMESTAMPTZ,
    review_reason    VARCHAR(512),           -- reason-bound: mandatory on review/dismiss
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_mf_anomaly_dedupe   ON mf_anomaly_findings(tenant_id, dedupe_key);
CREATE INDEX idx_mf_anomaly_tenant_status  ON mf_anomaly_findings(tenant_id, status);
CREATE INDEX idx_mf_anomaly_type           ON mf_anomaly_findings(tenant_id, finding_type, status);
CREATE INDEX idx_mf_anomaly_subject        ON mf_anomaly_findings(subject_type, subject_ref);

-- §11.1/§21 ranked-position + label snapshot backing the ranking-transparency
-- audit. Captured best-effort at sweep time while the request is live
-- (OFFERS_AVAILABLE / SELECTION_PENDING); the audit compares captured-then
-- labels against recomputed-now labels — divergence (RANKING_DRIFT) means the
-- ranking basis shown to the patient no longer matches the persisted record.
-- FUTURE SEAM (documented deferral): commitment-time capture inside the
-- selection machinery would close the sweep-cadence gap; a committed selection
-- with no captured snapshot is itself flagged (RANKING_SNAPSHOT_MISSING) —
-- the spec makes the snapshot mandatory, so its absence is a finding, not a
-- silent skip.
CREATE TABLE mf_ranking_snapshots (
    snapshot_id      VARCHAR(26)  PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    request_id       VARCHAR(26)  NOT NULL REFERENCES mf_marketplace_requests(request_id),
    offer_id         VARCHAR(26)  NOT NULL REFERENCES mf_fulfillment_offers(offer_id),
    position         INTEGER      NOT NULL,  -- 1-based rank shown in comparison order
    ranked_because_json JSONB     NOT NULL,  -- label array, e.g. ["SUITABLE","CHEAPEST"]
    price_total      NUMERIC(14,2),          -- price basis at capture (drift evidence)
    captured_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_mf_ranksnap_offer ON mf_ranking_snapshots(request_id, offer_id);
CREATE INDEX idx_mf_ranksnap_request     ON mf_ranking_snapshots(request_id);
