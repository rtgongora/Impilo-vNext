-- W17 — After-action linkage for emergency (and disaster) safety cases.
--
-- PCT already opens RITO cases on ACCEPTANCE_OVERDUE alerts and handover expiry, storing only
-- a rito_case_ref on the alert/handover row. Until now RITO had no reverse link type for an
-- emergency episode, so the after-action graph stopped at the case. This migration documents
-- the new link vocabulary; CaseClassification.LINK_TYPES is the enforcement point.

COMMENT ON TABLE rito.rit_case_link IS
  'Cross-SoR links from a RITO case. W17 adds EMERGENCY_EPISODE (PCT emergency_episode.id) and AFTER_ACTION (review artefact). target_system for emergency links is PCT (or DAIDZAI for disaster after-action).';

-- Seed an SLA policy for emergency acceptance / handover after-action cases (preview tenant).
INSERT INTO rito.rit_sla_policy
    (tenant_id, policy_key, name, applies_to_case_type, applies_to_severity,
     acknowledge_within_minutes, resolve_within_minutes, escalate_after_minutes, active)
VALUES
    ('00000000-0000-4000-8000-000000000001'::uuid,
     'emergency-acceptance-delay',
     'Emergency acceptance delay after-action',
     'SAFETY_CONCERN',
     'HIGH',
     60, 7 * 24 * 60, 24 * 60, TRUE),
    ('00000000-0000-4000-8000-000000000001'::uuid,
     'emergency-handover-expired',
     'Emergency handover expiry after-action',
     'SAFETY_INCIDENT',
     'HIGH',
     60, 7 * 24 * 60, 24 * 60, TRUE)
ON CONFLICT (tenant_id, policy_key) DO NOTHING;
