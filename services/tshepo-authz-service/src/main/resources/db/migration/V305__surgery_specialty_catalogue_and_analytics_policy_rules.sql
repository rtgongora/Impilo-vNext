-- ============================================================================
-- V305 — reachability for the specialty catalogue (SB-2/SB-4) and surgical analytics
-- (§23):
--
--   1. GET /internal/v1/surgery/specialties/indications?specialty=...
--   2. GET /internal/v1/surgery/specialties/templates?specialty=...
--   3. GET /internal/v1/surgery/analytics/indicators
--   4. GET /internal/v1/surgery/analytics/indicator?code=...
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3). Next free number after V304.
--
-- ══ THE TWO KNOWN PDP DEFECTS, designed around exactly as V300-V304 did ══
-- * Negatives are modelled as CORRECT-CADRE-ONLY ALLOW → NO_ALLOW_RULE, never as a path-pinned
--   DENY row (a conditional DENY that fails its own pin is skipped, not enforced).
-- * pathContainsSegment is segment-bounded and a TRAILING-SLASH pin can never match — every pin
--   below omits the trailing slash.
--
-- ══ ROUTE SHAPES ══ proven in SurgeryReachabilityRouteShapeTest against the real
-- deriveResourceType, not asserted from this comment:
--   .../surgery/specialties/indications  → "indications"
--   .../surgery/specialties/templates    → "templates"
--   .../surgery/analytics/indicators     → "indicators"
--   .../surgery/analytics/indicator      → "indicator"
-- Specialty and indicator codes travel as query parameters (?specialty=, ?code=), so they never
-- reach the segment walk (the free-text-code-in-path trap V303 already documented for episode
-- specialties).
--
-- ══ COLLISION SAFETY ══
-- * "indications"/"templates" must NOT pin "/surgery/episodes" — that family owns the
--   episode-scoped SHARED/LEAD specialty roster (V303, resource_type "specialties"). Catalogue
--   rows pin "/surgery/specialties" so a clinician ALLOW on the roster cannot fire for the
--   catalogue, and vice versa.
-- * "indicators"/"indicator" already have V302 procedures rows pinned to
--   "/procedures/analytics". Surgery rows therefore pin "/surgery/analytics" — without that
--   pin a surgeon ALLOW on procedures analytics would fire for surgery (or vice versa).
--
-- ══ ROLE SCOPING ══ (closed vocabulary — every role already exists in sibling migrations)
--   * Specialty catalogue (indications/templates): surgical care team including NURSE —
--     same read cadre V302 grants the episode itself (SURGEON, CONSULTANT, CLINICIAN, DOCTOR,
--     NURSE). Read-only seed content; picking a template is a clinical act on the theatre note.
--   * Analytics indicators: the same broad platform role set as V302 procedures analytics
--     (CLINICIAN, DOCTOR, NURSE, CONSULTANT, WARD_MANAGER) — a read-only catalogue of indicator
--     definitions and their computation status, not patient data. SURGEON is included because
--     the surgical pack catalogue is the surgeon's own quality surface.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ════════════ 1. Specialty indications — GET .../specialties/indications?specialty= ════════════
-- Pinned "/surgery/specialties" — NOT "/surgery/episodes" (episode roster is a different family).

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-indications-surgeon',
 'SURGEON: list specialty indications from the surgical domain pack catalogue.',
 'PROVIDER', 'SURGEON', 'indications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-indications-consultant',
 'CONSULTANT: list specialty indications from the surgical domain pack catalogue.',
 'PROVIDER', 'CONSULTANT', 'indications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-indications-clinician',
 'CLINICIAN: list specialty indications from the surgical domain pack catalogue.',
 'PROVIDER', 'CLINICIAN', 'indications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-indications-doctor',
 'DOCTOR: list specialty indications from the surgical domain pack catalogue.',
 'PROVIDER', 'DOCTOR', 'indications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-indications-nurse',
 'NURSE: list specialty indications (perioperative preparation).',
 'PROVIDER', 'NURSE', 'indications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),

-- ════════════ 2. Operative templates — GET .../specialties/templates?specialty= ════════════

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-templates-surgeon',
 'SURGEON: list specialty operative templates (SB-4 content for SB-5 note fill).',
 'PROVIDER', 'SURGEON', 'templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-templates-consultant',
 'CONSULTANT: list specialty operative templates.',
 'PROVIDER', 'CONSULTANT', 'templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-templates-clinician',
 'CLINICIAN: list specialty operative templates.',
 'PROVIDER', 'CLINICIAN', 'templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-templates-doctor',
 'DOCTOR: list specialty operative templates.',
 'PROVIDER', 'DOCTOR', 'templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-specialty-templates-nurse',
 'NURSE: list specialty operative templates (scrub / circulating preparation).',
 'PROVIDER', 'NURSE', 'templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/specialties"}', true),

-- ════════════ 3. Analytics catalogue — GET .../analytics/indicators ════════════
-- Pinned "/surgery/analytics" so these cannot fire for /procedures/analytics (V302).

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicators-surgeon',
 'SURGEON: view the surgical-pack analytics indicator catalogue and its computation status.',
 'PROVIDER', 'SURGEON', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicators-clinician',
 'CLINICIAN: view the surgical-pack analytics indicator catalogue.',
 'PROVIDER', 'CLINICIAN', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicators-doctor',
 'DOCTOR: view the surgical-pack analytics indicator catalogue.',
 'PROVIDER', 'DOCTOR', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicators-nurse',
 'NURSE: view the surgical-pack analytics indicator catalogue.',
 'PROVIDER', 'NURSE', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicators-consultant',
 'CONSULTANT: view the surgical-pack analytics indicator catalogue.',
 'PROVIDER', 'CONSULTANT', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicators-ward-manager',
 'WARD_MANAGER: view the surgical-pack analytics indicator catalogue (service planning).',
 'PROVIDER', 'WARD_MANAGER', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),

-- ════════════ 4. One analytics indicator — GET .../analytics/indicator?code= ════════════

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicator-surgeon',
 'SURGEON: view one surgical analytics indicator definition.',
 'PROVIDER', 'SURGEON', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicator-clinician',
 'CLINICIAN: view one surgical analytics indicator definition.',
 'PROVIDER', 'CLINICIAN', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicator-doctor',
 'DOCTOR: view one surgical analytics indicator definition.',
 'PROVIDER', 'DOCTOR', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicator-nurse',
 'NURSE: view one surgical analytics indicator definition.',
 'PROVIDER', 'NURSE', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicator-consultant',
 'CONSULTANT: view one surgical analytics indicator definition.',
 'PROVIDER', 'CONSULTANT', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-analytics-indicator-ward-manager',
 'WARD_MANAGER: view one surgical analytics indicator definition.',
 'PROVIDER', 'WARD_MANAGER', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/analytics"}', true)
ON CONFLICT DO NOTHING;
