-- =============================================================================
-- guidance-service V014 — Nompilo Service Advisory engine
--
-- Service advisories are Nompilo-mediated, DATA-DRIVEN platform messages: launch
-- notices, planned-maintenance/disruption notices, incident alerts and calls to
-- action. They are NEVER hard-coded in the frontend — the UI resolves the active,
-- in-window, audience-matching set from this table via the resolve API.
--
-- Read path (public lane):
--   service-side  AdvisoryResolveController  /internal/v1/guidance/advisory
--   experience-bff PublicGatewayAdvisoryBffController /internal/v1/public/gateway/advisory
--   → returns ONLY status=PUBLISHED, in-window, audience='public' advisories,
--     allow-listed fields, NO PII.
--
-- Authoring path (admin):
--   AdvisoryAdminController /internal/v1/guidance/advisory/admin — CRUD + lifecycle
--   Lifecycle: DRAFT → SCHEDULED → PUBLISHED → WITHDRAWN | EXPIRED
--   Governance: EMERGENCY_ALERT and IMPORTANT_DISRUPTION severities require an
--     approver (approved_by) to be set BEFORE they may be PUBLISHED.
--
-- severity : INFORMATIONAL | SERVICE_NOTICE | IMPORTANT_DISRUPTION | EMERGENCY_ALERT
-- variant  : MODAL | BANNER | BUBBLE | INLINE | EMERGENCY
-- status   : DRAFT | SCHEDULED | PUBLISHED | WITHDRAWN | EXPIRED
-- targeting (JSONB): audience(public|authenticated), deviceType, language,
--   province, district, serviceKey, appKey, facilityId, journeyStage,
--   incidentRef, releaseRef
-- primary_actions (JSONB): [{ "label": "...", "href": "..." }]
-- =============================================================================

CREATE TABLE guidance.service_advisory (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL DEFAULT 'national-spine',
    title            VARCHAR(512) NOT NULL,
    body             TEXT         NOT NULL,
    severity         VARCHAR(32)  NOT NULL,   -- INFORMATIONAL | SERVICE_NOTICE | IMPORTANT_DISRUPTION | EMERGENCY_ALERT
    variant          VARCHAR(16)  NOT NULL,   -- MODAL | BANNER | BUBBLE | INLINE | EMERGENCY
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT | SCHEDULED | PUBLISHED | WITHDRAWN | EXPIRED
    targeting        JSONB,                   -- audience/device/language/geo/service/journey/incident/release selectors
    starts_at        TIMESTAMPTZ,             -- window open (NULL = immediate)
    expires_at       TIMESTAMPTZ,             -- window close (NULL = no expiry)
    primary_actions  JSONB,                   -- [{label, href}] call-to-action list
    created_by       VARCHAR(255),
    approved_by      VARCHAR(255),            -- required before PUBLISH for EMERGENCY/IMPORTANT severities
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_service_advisory_resolve
    ON guidance.service_advisory (tenant_id, status, starts_at, expires_at);

-- Impression / interaction analytics. anon_dismissal_key is an opaque, caller-supplied
-- key (NO PII) that lets the public lane record a dismissal without identifying a person.
CREATE TABLE guidance.advisory_impression (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    advisory_id         UUID NOT NULL REFERENCES guidance.service_advisory (id) ON DELETE CASCADE,
    tenant_id           VARCHAR(255) NOT NULL DEFAULT 'national-spine',
    event               VARCHAR(16) NOT NULL,   -- VIEW | DISMISS | FEEDBACK
    anon_dismissal_key  VARCHAR(255),           -- opaque, no PII
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_advisory_impression_advisory
    ON guidance.advisory_impression (advisory_id, event);

-- ── Seed: one launch advisory (public, PUBLISHED, no window bound) ────────────
INSERT INTO guidance.service_advisory
    (tenant_id, title, body, severity, variant, status, targeting, primary_actions, created_by, approved_by)
VALUES
    ('national-spine',
     'Help us build the future of health in Zimbabwe',
     'Impilo is Zimbabwe''s National Health Operating System — one trusted place for your health identity, records and services. We are building it together, and you are early. Explore what is here today, tell us what matters to you, and get involved in shaping what comes next.',
     'INFORMATIONAL',
     'MODAL',
     'PUBLISHED',
     '{"audience": "public"}'::jsonb,
     -- Single JSON literal: the ::jsonb cast binds tighter than ||, so a
     -- concatenated form would cast only the last fragment and fail.
     '[{"label": "Explore", "href": "/welcome"}, {"label": "Get involved", "href": "/get-involved"}, {"label": "Report a problem", "href": "/welcome/report"}, {"label": "View service status", "href": "/status"}]'::jsonb,
     'system',
     'system');
