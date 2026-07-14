-- inpatient-service V026 — theatre-day readiness board depth (Wave 2)
-- Records resolve-blocker actions routed to the owning service. The readiness
-- checks themselves reuse the existing inpatient.procedure_readiness_check
-- table (V018); this adds the resolution audit trail + the widened domain vocab.

CREATE TABLE inpatient.procedure_blocker_resolution (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL
        REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    domain              VARCHAR(24) NOT NULL,   -- BED | RECOVERY | ICU | INSTRUMENT_SET | IMPLANT |
                                                -- FASTING | IDENTITY | PROPHYLAXIS | PREGNANCY |
                                                -- CLEARANCE | CONSENT | ROOM | TEAM | EQUIPMENT | BLOOD
    action              VARCHAR(48) NOT NULL,   -- e.g. ROUTE_TO_OWNER | ACKNOWLEDGE | OVERRIDE
    routed_owner        VARCHAR(32) NOT NULL,   -- tuso | vashandi | mvumo | inpatient | asset-registry | ...
    owner_ref           VARCHAR(256),
    note                TEXT,
    resolved_by         VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_blocker_resolution_episode
    ON inpatient.procedure_blocker_resolution (episode_id, domain, created_at DESC);
