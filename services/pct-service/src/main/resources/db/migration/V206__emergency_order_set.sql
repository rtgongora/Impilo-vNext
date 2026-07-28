-- Order set instance/item tracking (W7b) — the pct side of "a bundle a clinician invokes as one
-- action". CKP content (step_type='ORDER_SET', see ckp V202) holds what a bundle IS; this table pair
-- tracks what actually happened when one was invoked for a specific patient: which items were
-- ordered, which were explicitly declined and why, and which are still pending a decision.
--
-- Anchored on emergency_episode, not ed_visit, per R2 — not every emergency episode has an ED visit
-- (a ward arrest gets a bundle invoked too), so anchoring on the episode is what makes order sets
-- usable from every entry route rather than only the ED lane.
--
-- order_set_code and each item's order_code are content-reference strings, not foreign keys — CKP is
-- a different service with its own database, the same pattern this pack already uses for
-- trauma_episode_id (a plain correlating id, no cross-service FK possible).

CREATE TABLE emergency_order_set_instance (
    instance_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL,
    episode_id           UUID        NOT NULL REFERENCES emergency_episode (episode_id),
    visit_id             UUID,
    pathway_session_id   UUID,

    order_set_code       VARCHAR(64) NOT NULL,
    order_set_name       VARCHAR(255),

    status               VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',

    invoked_by           VARCHAR(128) NOT NULL,
    invoked_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_eosi_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED'))
);

CREATE INDEX idx_emergency_order_set_instance_episode
    ON emergency_order_set_instance (tenant_id, episode_id, invoked_at DESC);

CREATE TABLE emergency_order_set_item (
    item_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL,
    instance_id          UUID        NOT NULL REFERENCES emergency_order_set_instance (instance_id) ON DELETE CASCADE,

    order_code           VARCHAR(64) NOT NULL,
    order_label          VARCHAR(255),
    order_type           VARCHAR(24),

    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Set once ORDERED and only when this item produced a real diagnostic order. Nullable and
    -- unenforced for medication/observation items — those get their own correlation in a later
    -- wave (pct emergency_medication_admin, W8); this column is not stretched to cover them.
    diagnostic_order_id  UUID,

    ordered_at           TIMESTAMPTZ,
    ordered_by           VARCHAR(128),

    declined_at          TIMESTAMPTZ,
    declined_by          VARCHAR(128),
    -- THE plan's explicit requirement for this table: "decline requires a reason". A decline with no
    -- stated reason is unauditable later — was it clinically inappropriate, already done elsewhere,
    -- or just skipped? — which is exactly the ambiguity a reason field exists to remove.
    decline_reason       TEXT,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_eosit_status CHECK (status IN ('PENDING', 'ORDERED', 'DECLINED')),

    CONSTRAINT chk_eosit_ordered_pair CHECK (
        status <> 'ORDERED' OR (ordered_at IS NOT NULL AND ordered_by IS NOT NULL)),

    CONSTRAINT chk_eosit_declined_pair CHECK (
        status <> 'DECLINED' OR (
            declined_at IS NOT NULL AND declined_by IS NOT NULL
            AND decline_reason IS NOT NULL AND btrim(decline_reason) <> ''))
);

CREATE INDEX idx_emergency_order_set_item_instance
    ON emergency_order_set_item (instance_id);

COMMENT ON TABLE emergency_order_set_instance IS
    'One invocation of a CKP-defined order-set bundle (step_type=ORDER_SET) for a specific emergency '
    'episode. Repeat invocation of the same order_set_code on one episode is a normal clinical action '
    '(e.g. re-ordering a sepsis bundle for a second deterioration), not a defect — deliberately no '
    'anti-duplicate constraint, unlike emergency_alert/emergency_handover where a second open row IS '
    'the defect.';
COMMENT ON COLUMN emergency_order_set_item.decline_reason IS
    'Required when status=DECLINED (chk_eosit_declined_pair) — the plan''s explicit requirement for '
    'this table. A decline with no reason is unauditable: clinically inappropriate, already done '
    'elsewhere, or simply skipped are very different facts.';
