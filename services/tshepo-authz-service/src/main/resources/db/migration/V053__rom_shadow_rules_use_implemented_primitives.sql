-- =====================================================================================
-- TSHEPO-authz :: V053 — the ROM shadow rules, re-expressed in primitives the PDP has
--
-- The regulatory shadow rows (V045 org isolation, V046 committee docket, V047 HPA oversight)
-- were authored against condition keys the engine does not implement — require_token_org_match,
-- deny_when_org_mismatch, require_docket_assignment, deny_when_not_docketed,
-- require_escalation_grant, deny_operational_lanes — and against path_contains pins that match no
-- route the estate actually serves. Both defects are silent, and the second compounds the first.
--
-- WHY AN UNIMPLEMENTED KEY IS WORSE THAN A NO-OP.
--
-- evaluateConditions checks the keys it knows and then returns true. An unrecognised key is
-- therefore not "ignored so the rule sits inert" — the rule evaluates on whatever remains, so it
-- fires more broadly than it reads. On a DENY that is dangerous, and DENY wins: seeded
-- rom-hpa-no-operational-access read as "deny HPA the operational lanes under /regulatory/", but on
-- activation would have denied HPA_OVERSIGHT_OFFICER *every* /regulatory/ path — swallowing the two
-- oversight ALLOW rows seeded immediately beside it. (V053's companion engine change makes an
-- unknown key a non-match with a WARN, so this can never recur; this migration additionally repairs
-- the rows so activating them does what they say.)
--
-- WHY THE PATHS DID NOT MATCH EITHER.
--
-- pathContainsSegment is segment-bounded: a pin must be followed by "/" or end-of-path. A pin that
-- itself ends in "/" — "/regulatory/" — can therefore only match a path that ENDS at that slash,
-- which no real route does; every regulatory route continues (".../regulatory/console/..."). And the
-- console segment the browser actually calls sits between: the ext_authz :path is
-- /internal/v1/regulatory/console/... and /internal/v1/me/regulatory/..., never /regulatory/hearings
-- or /regulatory/oversight/indicators. The pins are corrected here to the routes the shell emits.
--
-- WHAT THE PDP CAN AND CANNOT SEE.
--
-- Three of these rows compare a token claim against a per-request TARGET the PDP never receives:
--   * cross-council isolation needs the target ORG — there is no organisation trust header; the org
--     lives only inside the WORK_CONTEXT token (DutyContext.orgId), so the PDP can read the actor's
--     own org but not the org of the record being reached.
--   * committee docket scoping needs to know whether THIS case is docketed to the member — a
--     per-record fact absent from the request.
--   * the HPA granted-case read needs an oversight_escalation_grant bound to THIS case — likewise
--     absent, and there is no per-case oversight route at all.
-- These are exactly the comparisons the ROM design placed in the impilo.authz rego (wired + flipped
-- at ROM-W11 ENFORCE via CZO), backed in-band today by the service layer (HearingDocketService
-- myDocket, DisciplinaryProceedingService, OversightService). A DB policy rule cannot express them
-- honestly, so shipping one that pretends to — active or not — is the false assurance this whole
-- seam exists to remove. Those rows are WITHDRAWN here (active=false, marked so a future operator
-- does not flip them), their intent explicitly delegated to rego + service, not lost.
--
-- The rows the PDP genuinely can enforce are re-expressed with implemented primitives and real
-- routes, and LEFT active=false: turning ROM enforcement on is the ROM lane's / PO's decision, and
-- happens through the rego flip at W11. The deliverable of this migration is only that flipping any
-- surviving row becomes SAFE — it grants or denies exactly what its description says, no wider.
--
-- No PolicyEngine edit is required BY THIS FILE — every key below is already implemented
-- (path_contains). Golden tenant 00000000-0000-0000-0000-000000000001.
-- =====================================================================================

-- ── Kept, re-expressed: regulatory personnel may act on the regulatory surfaces ──────────
-- Own-org scoping is delivered by the token being org-anchored (services read DutyContext.orgId)
-- and by rego at ENFORCE; the PDP rule grants the surface to a regulatory-duty session. purpose
-- REGULATORY_DUTY is the discriminator and is intentionally kept: until the shell emits it the
-- ALLOW is unreachable and therefore harmless (fail-closed), never over-granting.
UPDATE tshepo_authz.policy_rule
SET conditions = '{"path_contains": ["/regulatory/console", "/me/regulatory"]}'
WHERE name = 'rom-regulatory-own-org-allow';

-- ── Kept, re-expressed: a committee member may reach the hearings surface ────────────────
-- WHICH cases they may read (docketed-to-them) is a per-record fact the PDP cannot see; that
-- filter is HearingDocketService.myDocket today and rego at ENFORCE.
UPDATE tshepo_authz.policy_rule
SET conditions = '{"path_contains": "/regulatory/console/hearings"}'
WHERE name = 'rom-committee-member-docket-read';

-- ── Kept, re-expressed: HPA may read the consolidated cross-council indicators ───────────
-- There is no dedicated indicators route; the indicators are produced by the generic report runner
-- keyed to reg.oversight.cross_council_indicators (reporting V003). The pin is scoped to that exact
-- report key so the ALLOW does not widen into every report the runner can serve.
UPDATE tshepo_authz.policy_rule
SET conditions = '{"path_contains": "/regulatory/console/reports/reg.oversight.cross_council_indicators"}'
WHERE name = 'rom-hpa-oversight-aggregates';

-- ── Kept, re-expressed: HPA is denied the council OPERATIONAL lanes (belt-and-braces) ────
-- Named precisely, exactly as V052 pinned the country root's denials, so the DENY cannot widen into
-- the oversight reads (indicators, and the granted-case read once it exists) the same role needs.
-- HPA oversight is aggregate + granted-case only; it must not enter a council's operational
-- workspace. This is a DENY, so it can only ever reduce — but it stays active=false with the pack.
UPDATE tshepo_authz.policy_rule
SET conditions = '{"path_contains": ["/regulatory/console/disciplinary", "/regulatory/console/hearings", "/regulatory/console/applications", "/regulatory/console/imports", "/regulatory/console/practice-establishments"]}'
WHERE name = 'rom-hpa-no-operational-access';

-- ── Withdrawn: comparisons the PDP structurally cannot make ──────────────────────────────
-- Each of these needs a per-request target the request does not carry (target org / this case's
-- docket / this case's oversight grant). They are enforced by the service layer now and by the
-- impilo.authz rego at ROM-W11 ENFORCE. Withdrawing them at the PDP removes documentation that
-- would read as enforcement without being it. active=false was their birth state; the explicit
-- active=false here MARKS them withdrawn (not merely awaiting a flip) so no operator activates a
-- rule the engine cannot evaluate. rom-committee-docket-only is additionally a duplicate of
-- rom-committee-member-docket-read (the V046 elaboration of the same intent).
UPDATE tshepo_authz.policy_rule
SET active = false,
    description = 'WITHDRAWN (V053): cross-council isolation compares the token org against the '
        'target org, which the PDP never receives. Enforced by the impilo.authz rego at ENFORCE and '
        'by org-scoped service queries in-band. Do not activate.'
WHERE name = 'rom-cross-council-deny';

UPDATE tshepo_authz.policy_rule
SET active = false,
    description = 'WITHDRAWN (V053): superseded by rom-committee-member-docket-read, and docket '
        'scoping is a per-record comparison the PDP cannot see. Enforced by HearingDocketService '
        'and by rego at ENFORCE. Do not activate.'
WHERE name = 'rom-committee-docket-only';

UPDATE tshepo_authz.policy_rule
SET active = false,
    description = 'WITHDRAWN (V053): "not docketed to me" is a per-record comparison the PDP cannot '
        'make. Enforced by HearingDocketService.myDocket and by rego at ENFORCE. Do not activate.'
WHERE name = 'rom-committee-non-docket-deny';

UPDATE tshepo_authz.policy_rule
SET active = false,
    description = 'WITHDRAWN (V053): a per-case oversight read needs an oversight_escalation_grant '
        'bound to the case (absent from the request) and there is no per-case oversight route. '
        'Enforced by OversightService and by rego at ENFORCE. Do not activate.'
WHERE name = 'rom-hpa-oversight-granted-case';
