-- Procedures :: V009 — infection prevention and control requirement depth (pipeline §21).
--
-- Closes the four standards this domain's own coverage-exclusions.json already committed to
-- P11 by name at Wave 0.4 (docs/clinical-governance/procedures/coverage-exclusions.json):
-- IPC.CORE.HAND_HYGIENE, IPC.CORE.ASEPTIC_TECHNIQUE, IPC.CORE.STERILE_PROCESSING,
-- IPC.CORE.INJECTION_SAFETY. Their own revisitCondition text says exactly how: "P11 ships
-- infection-prevention readiness as catalogue requirements with named owners per unresolved
-- item" — this is that, using the EXISTING procedure_requirement model (requirement_kind
-- 'IPC' and 'STERILE_PROCESSING' have both been valid since V002; a generic sterile checkbox
-- is not what the specification asks for, and neither is a new table — the catalogue already
-- has the right shape for this, it was simply never seeded with real depth).
--
-- UNIVERSAL VS SETTING-SCOPED, decided per item rather than applied uniformly:
--   - Hand hygiene, aseptic technique and injection safety (single-use/exposure/waste) are
--     genuinely universal WHO IPC core practices for ANY invasive intervention — every entry
--     in this catalogue qualifies by construction (this is the Clinical Procedures Pipeline;
--     everything in it is an invasive or interventional procedure). Seeded across all 66.
--   - PPE and skin preparation are seeded universally too, but their prompt is written to
--     reflect that appropriate PPE/prep varies by exposure risk and site rather than naming one.
--   - Sterile-instrument reprocessing limits only make sense where a reprocessed (not
--     single-use) instrument set or endoscope is actually used — scoped to procedures whose
--     permitted_settings includes THEATRE or ENDOSCOPY, broadening the two procedure-specific
--     rows V003 already seeded (CSSD-SET, CSSD-SCOPE) rather than duplicating their intent.
--
-- ON CONFLICT DO NOTHING throughout: V003's two existing IPC-ASEPTIC rows (PROC-LUMBAR-PUNCTURE,
-- PROC-CHEST-DRAIN) are left exactly as they are; this only fills the other 64.

\set tenant '00000000-0000-4000-8000-000000000001'

-- Hand hygiene — IPC.CORE.HAND_HYGIENE. The WHO Five Moments, self-attested by the performing
-- clinician like every other point-of-care IPC step already in this catalogue (no peer service
-- resolves "did you wash your hands" — resolver_service NULL, matching IPC-ASEPTIC's own pattern).
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'IPC', 'IPC-HAND_HYGIENE',
       'Hand hygiene performed at the WHO Five Moments before and during the procedure',
       'MANDATORY', 'PERFORMING_CLINICIAN', NULL, FALSE, 'BLOCK', 41
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

-- Aseptic technique — IPC.CORE.ASEPTIC_TECHNIQUE, broadened from V003's two-procedure seed to
-- the whole catalogue. Same requirement_code as V003's own rows, so ON CONFLICT DO NOTHING
-- leaves those two untouched and only adds the remaining 64.
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'IPC', 'IPC-ASEPTIC', 'Aseptic technique and sterile field',
       'MANDATORY', 'PERFORMING_CLINICIAN', NULL, FALSE, 'BLOCK', 42
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

-- PPE — folded under aseptic technique's own standard family rather than a separate WHO
-- citation of its own (the specification's "PPE" gap sits alongside skin prep and aseptic
-- technique in the same preoperative-measures cluster).
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'IPC', 'IPC-PPE',
       'Personal protective equipment appropriate to this procedure''s exposure risk is worn',
       'MANDATORY', 'PERFORMING_CLINICIAN', NULL, FALSE, 'BLOCK', 43
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

-- Skin preparation — where the procedure breaks skin or accesses a normally sterile space.
-- Every procedure in this catalogue is invasive by construction, so this too is seeded
-- universally; the prompt itself names "where applicable" rather than assuming a single
-- antiseptic for every site.
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'IPC', 'IPC-SKIN_PREP',
       'Skin or access site prepared with an appropriate antiseptic before the intervention, where applicable',
       'MANDATORY', 'PERFORMING_CLINICIAN', NULL, FALSE, 'BLOCK', 44
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

-- Injection safety, single-use discipline, exposure incident and waste — IPC.CORE.INJECTION_SAFETY.
-- The three named gaps (single-use, exposure incident, waste) belong to one WHO guideline
-- family and are folded into one requirement rather than three near-duplicate rows; the
-- exposure-incident half is what the standards baseline itself flagged as "the notable one: a
-- staff safety event with a reporting obligation" — named explicitly in the prompt so it is not
-- read as merely a sharps-disposal checkbox.
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'IPC', 'IPC-INJECTION_SAFETY',
       'Single-use devices used once only; any sharps exposure incident is reported immediately as a staff safety event; clinical waste segregated per protocol',
       'MANDATORY', 'PERFORMING_CLINICIAN', NULL, FALSE, 'BLOCK', 45
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

-- Sterile-instrument reprocessing limits — IPC.CORE.STERILE_PROCESSING's remaining gap after
-- V003's CSSD-SET/CSSD-SCOPE rows: those prove the cycle happened, not that the declared reuse/
-- cycle limit was respected. Scoped to THEATRE/ENDOSCOPY settings — a reprocessed instrument set
-- or endoscope, not single-use bedside equipment, is what this requirement is actually about.
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'STERILE_PROCESSING', 'CSSD-REPROCESSING_LIMITS',
       'Reprocessed instrument set or endoscope is within its declared reuse/cycle limit, not merely sterilised',
       'MANDATORY', 'CSSD_MANAGER', 'tuso-service', FALSE, 'BLOCK', 46
FROM procedures.procedure_definition d
WHERE d.tenant_id = :'tenant'
  AND (d.permitted_settings ? 'THEATRE' OR d.permitted_settings ? 'ENDOSCOPY')
ON CONFLICT DO NOTHING;
