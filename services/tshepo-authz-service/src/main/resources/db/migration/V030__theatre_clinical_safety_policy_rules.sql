-- ============================================================================
-- V030 — Theatre clinical-safety (Wave 1) policy rules.
-- ext_authz ALLOW rules for the NEW blood / transport / specimen / count segments under
-- /internal/v1/theatre/cases/{id}/... and the anaesthesia chart under
-- /internal/v1/procedures/{id}/anaesthesia/chart, served by inpatient-service.
-- Mirrors V029 (theatre perioperative) style: actor_type=PROVIDER, theatre clinical roles, action POST,
-- facility_scope true, purpose TREATMENT.
--
-- Next free authz migration: latest was V029 (theatre perioperative). This clinical-safety wave = V030.
--
-- COLLISION SAFETY: the PDP derives resource_type from the last path segment, which collides for
-- generic words (request/issue/administer/resolve/movement/specimen/pacu/counts/chart). Each rule pins
-- path_contains "/theatre/" (or "/anaesthesia/chart") so a same-segment endpoint elsewhere is NOT granted.
--
-- Role scoping:
--   * blood request/issue/resolve → SURGEON + ANAESTHETIST (order & route blood)
--   * blood administer (bedside)  → NURSE + ANAESTHETIST (two-step bedside administration)
--   * transport (movement/specimen) + pacu → SURGEON + ANAESTHETIST + NURSE
--   * specimen acknowledge-critical → SURGEON + ANAESTHETIST (clinical acknowledgement)
--   * surgical counts → NURSE + SURGEON (scrub nurse records; a discrepancy must never be blocked)
--   * anaesthesia chart → ANAESTHETIST + NURSE
-- The in-service guards bind subject/scope/count/critical state; these gateway rules make the routes
-- reachable. DENY-wins + first-matching-ALLOW semantics unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Blood request (POST .../blood/request) — `request` segment; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-request-surgeon',
 'SURGEON: request theatre blood (MADI group & screen).',
 'PROVIDER', 'SURGEON', 'request', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-request-anaesthetist',
 'ANAESTHETIST: request theatre blood.',
 'PROVIDER', 'ANAESTHETIST', 'request', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Blood issue & transport (POST .../blood/issue) — `issue` segment; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-issue-surgeon',
 'SURGEON: issue & transport reserved blood (NHUME).',
 'PROVIDER', 'SURGEON', 'issue', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-issue-anaesthetist',
 'ANAESTHETIST: issue & transport reserved blood.',
 'PROVIDER', 'ANAESTHETIST', 'issue', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Blood administer (POST .../blood/administer) — `administer`; NURSE + ANAESTHETIST ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-administer-nurse',
 'NURSE: bedside two-step administration of blood.',
 'PROVIDER', 'NURSE', 'administer', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-administer-anaesthetist',
 'ANAESTHETIST: bedside two-step administration of blood.',
 'PROVIDER', 'ANAESTHETIST', 'administer', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Blood resolve (POST .../blood/resolve) — `resolve`; SURGEON + ANAESTHETIST ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-resolve-surgeon',
 'SURGEON: resolve/reconcile the transfusion outcome.',
 'PROVIDER', 'SURGEON', 'resolve', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-blood-resolve-anaesthetist',
 'ANAESTHETIST: resolve/reconcile the transfusion outcome.',
 'PROVIDER', 'ANAESTHETIST', 'resolve', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Patient movement (POST .../transport/movement) — `movement`; all theatre roles ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-transport-movement-surgeon',
 'SURGEON: request patient movement (ward/theatre/PACU).',
 'PROVIDER', 'SURGEON', 'movement', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-transport-movement-anaesthetist',
 'ANAESTHETIST: request patient movement.',
 'PROVIDER', 'ANAESTHETIST', 'movement', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-transport-movement-nurse',
 'NURSE: request patient movement.',
 'PROVIDER', 'NURSE', 'movement', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 52, '{"path_contains": "/theatre/cases/"}', true),

-- ── Specimen transport (POST .../transport/specimen) — `specimen`; all theatre roles ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-transport-specimen-surgeon',
 'SURGEON: request specimen transport to lab.',
 'PROVIDER', 'SURGEON', 'specimen', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-transport-specimen-nurse',
 'NURSE: request specimen transport to lab.',
 'PROVIDER', 'NURSE', 'specimen', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── PACU entry (POST .../pacu) — `pacu`; all theatre roles ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-pacu-enter-surgeon',
 'SURGEON: enter PACU (auto THEATRE_TO_PACU movement).',
 'PROVIDER', 'SURGEON', 'pacu', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-pacu-enter-anaesthetist',
 'ANAESTHETIST: enter PACU.',
 'PROVIDER', 'ANAESTHETIST', 'pacu', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-pacu-enter-nurse',
 'NURSE: enter PACU.',
 'PROVIDER', 'NURSE', 'pacu', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 52, '{"path_contains": "/theatre/cases/"}', true),

-- ── Critical acknowledgement (POST .../acknowledge-critical) — segment; SURGEON + ANAESTHETIST ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-ack-critical-surgeon',
 'SURGEON: acknowledge a critical pathology result.',
 'PROVIDER', 'SURGEON', 'acknowledge-critical', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-ack-critical-anaesthetist',
 'ANAESTHETIST: acknowledge a critical pathology result.',
 'PROVIDER', 'ANAESTHETIST', 'acknowledge-critical', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Surgical counts (POST .../counts) — `counts`; NURSE + SURGEON ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-count-record-nurse',
 'NURSE: record a swab/instrument/needle count.',
 'PROVIDER', 'NURSE', 'counts', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-count-record-surgeon',
 'SURGEON: record a swab/instrument/needle count.',
 'PROVIDER', 'SURGEON', 'counts', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Anaesthesia chart (POST .../anaesthesia/chart) — `chart`; ANAESTHETIST + NURSE; pinned distinctly ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-anaesthesia-chart-anaesthetist',
 'ANAESTHETIST: record an anaesthesia chart entry.',
 'PROVIDER', 'ANAESTHETIST', 'chart', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/anaesthesia/chart"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-anaesthesia-chart-nurse',
 'NURSE: record an anaesthesia chart entry.',
 'PROVIDER', 'NURSE', 'chart', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/anaesthesia/chart"}', true)
ON CONFLICT DO NOTHING;
