-- ============================================================================
-- V057 — COMMUNITY_OUTREACH and SPECIMEN_TRANSPORT become governed WorkModes.
--
-- Both already existed as work: the provider app shipped outreach screens that
-- record screenings and immunizations against a person, and courier screens
-- that capture seals, cold-chain readings and proof of delivery. Neither had a
-- WorkMode, so neither had an anchor requirement, a clinical-data envelope, a
-- purpose of use, or a policy rule — the app's `AppMode` carried them as
-- ungoverned local navigation, which is to say the authority was assumed.
--
-- The clinical-data envelopes are the point of the pair and they are opposite:
--
--   COMMUNITY_OUTREACH  IDENTIFIED — the work IS recording a screening or an
--                       immunization against a named person. A mode that could
--                       not do that would describe none of the actual work.
--   SPECIMEN_TRANSPORT  NONE — a courier must be able to prove what they
--                       carried and under what conditions, and must never be
--                       able to read what it said. Logistics authority is not
--                       clinical authority, and this is where that is enforced.
--
-- `offline` deliberately does NOT become a mode. Architecture decision 11
-- already rules it is connectivity state layered over whatever context is
-- active; a mode for it would invent an authority that describes nothing and
-- would have to answer "offline to do what?" with every other mode's answer.
--
-- FACILITY_OR_PROGRAMME is a new anchor kind. Outreach is dispatched either by
-- a facility (a clinic's own outreach team) or by the programme running the
-- campaign (EPI, mass drug administration). Forcing it into the existing
-- FACILITY_OR_ORGANISATION would make programme-run outreach unmintable, since
-- programme and organisation are distinct anchors in this model.
-- ============================================================================

-- ── Part A: widen the anchor vocabulary ────────────────────────────────────
ALTER TABLE tshepo_authz.work_mode_catalog
    DROP CONSTRAINT IF EXISTS chk_work_mode_anchor_kind;

ALTER TABLE tshepo_authz.work_mode_catalog
    ADD CONSTRAINT chk_work_mode_anchor_kind
    CHECK (anchor_kind IN ('FACILITY','ORGANISATION','JURISDICTION','PROGRAMME',
                           'FACILITY_OR_ORGANISATION','FACILITY_OR_PROGRAMME'));

-- ── Part B: the two modes ──────────────────────────────────────────────────
INSERT INTO tshepo_authz.work_mode_catalog
    (tenant_id, mode_code, display_label, anchor_kind, clinical_data_access, default_purpose_of_use, requires_provider)
VALUES
-- requires_provider: outreach administers vaccines and records clinical
-- findings, so it is regulated professional practice. A courier is not a
-- clinician and requiring a Provider ID would make the mode unusable by the
-- people who actually do the work.
('00000000-0000-0000-0000-000000000001'::uuid, 'COMMUNITY_OUTREACH', 'Community Outreach', 'FACILITY_OR_PROGRAMME',    'IDENTIFIED', 'TREATMENT',  true),
('00000000-0000-0000-0000-000000000001'::uuid, 'SPECIMEN_TRANSPORT', 'Specimen Transport', 'FACILITY_OR_ORGANISATION', 'NONE',       'OPERATIONS', false)
ON CONFLICT ON CONSTRAINT uq_work_mode_catalog DO NOTHING;

-- ── Part C: which duty templates may hold them ─────────────────────────────
-- Grants only; holding a template does not grant the mode until an ACTIVE
-- assignment resolves a context that offers it.
INSERT INTO tshepo_authz.role_template_mode
    (tenant_id, role_template_id, mode_code, default_mode, rank)
VALUES
-- Community health workers work outreach by default; those with a facility
-- duty may also take clinical care at the facility they report to.
('00000000-0000-0000-0000-000000000001'::uuid, 'COMMUNITY_HEALTH_WORKER',  'COMMUNITY_OUTREACH', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'VILLAGE_HEALTH_WORKER',    'COMMUNITY_OUTREACH', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'OUTREACH_NURSE',           'COMMUNITY_OUTREACH', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'OUTREACH_NURSE',           'CLINICAL_CARE',      false, 1),
-- A general nurse may be rostered to an outreach round; clinical care stays
-- their default, so this does not change anyone's normal posture.
('00000000-0000-0000-0000-000000000001'::uuid, 'NURSE_GENERAL',            'COMMUNITY_OUTREACH', false, 2),

-- Specimen transport. Deliberately NOT granted to any clinical template: a
-- clinician needing to move a specimen does so under their clinical duty, and
-- granting them a NONE-access mode would only let them drop their own
-- clinical envelope, which is not a capability anyone needs.
('00000000-0000-0000-0000-000000000001'::uuid, 'SPECIMEN_COURIER',         'SPECIMEN_TRANSPORT', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'SAMPLE_TRANSPORT_OFFICER', 'SPECIMEN_TRANSPORT', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'LAB_LOGISTICS_OFFICER',    'SPECIMEN_TRANSPORT', true,  0)
ON CONFLICT ON CONSTRAINT uq_role_template_mode DO NOTHING;
