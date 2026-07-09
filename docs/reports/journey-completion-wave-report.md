# Practical Journey Completion Wave — build-phase report

Date: 2026-07-09 · Branch: `claude/staging-ux-orchestration-remediation-Yypyl` · Tip at report: `5721c7fce`
Directive: real end-to-end product usability — dead action buttons are P0 defects; journeys proven per persona, not as system admin.

## 1. The audit reframe (what was actually broken)

A three-agent code audit against HEAD found that the headline "dead" journeys — Fundo New Course,
PCT registration→queue→encounter, telemedicine sessions, the PACS/diagnostics order→result loop,
Varapi issuance — are **wired end-to-end** and most were previously proven live (Scenario A–D steel
threads, session-suite media proofs). The estate was current (certified commit = deployed UI tip).
The experienced "false green" had five real mechanisms, all now addressed:

1. **Role/persona truth was broken.** The realm had 9 roles and 8 users. The frontend referenced
   realm roles that did not exist (`HIE_ADMIN`, `PUBLIC_HEALTH_OFFICER`), and the Dispatch Ops
   launcher was gated on a role group that never existed — invisible to every user, the archetypal
   dead button. There were no trainer/clerk/radiographer/specialist/regulator identities, so
   PO walkthroughs ran as system admin and hit gates and empty contexts.
2. **Governance surfaces were never built** (IATG console, approval queues) — backends existed.
3. **Workforce intake was not composed** into a journey — the pieces (wgv batch tables, varapi
   issuance, invitation service) existed separately.
4. **Onboarding invitation emails were dead-by-construction** — the BFF sent a request shape the
   notification service rejects (missing `to`, lowercase channel, unregistered dynamic template
   keys). Every invitation 400'd and was swallowed as `pending_backend`.
5. **Six named services were absent from the Start Menu**; channels defaulted to stub with no
   configuration path; and there was no browser-level persona acceptance to catch any of this.

## 2. What landed (all committed + pushed, tests green)

| Area | Delivered | Evidence |
|---|---|---|
| Notification templates | V012: GOVERNANCE_ONBOARDING_INVITATION, PROVIDER_ID_ISSUED(+EMAIL), ACCESS_REQUEST_APPROVED/REJECTED(+EMAIL), COURSE_ASSIGNED, TELECONSULT_INVITE(+SMS), DIAGNOSTIC_RESULT_READY(+SMS, patient-safe), FACILITY_CLAIM_DECISION(+EMAIL) | notification-service 47 green |
| Invitation contract fix | GovernanceInvitationService now speaks the real NotifyRequest contract (stable key, `to`, EMAIL, string variables, `impilo.governance.activation-base-url`) | GovernanceInvitationServiceTest 6 green |
| Start Menu command centre | +8 service apps (Khuluma, Rito, Simba, Ubomi, Dura, Daidzai, Vashandi, Telemedicine — hrefs verified), 19 deep-action commands, quick-actions row, pinned section, needs-attention unread banner; dead `OPERATIONS` gate fixed; **no-dead-gates registry test** + 9-persona visibility matrix test | app-registry tests 15→21 green; launcher dead-end gate PASS |
| Persona Truth Pack | Realm +TRAINER/HIE_ADMIN/PUBLIC_HEALTH_OFFICER; 8 new users (clerk.dube, dr.gwena@Parirenyatwa, rad.nkomo, pharm.zimba→existing PROV-ZW-00004, trainer.chikafu, learner.tembo, regulator.hpcz, iatg.gono); varapi/wgv seeds 14/15; idempotent `scripts/operator/seed-persona-truth-pack.sh` with per-persona chain verification; `docs/demo/persona-truth-pack.md`; `LEARNING_AUTHOR` role group | seeds follow proven 12/13 idiom; verify stage is the test |
| IATG Trust Console | `/registry-admin/trust-console`: pending provider access requests (varapi V022 review/decide lane), cross-facility facility-admin claims (tuso), org onboarding (reuses fulfilment so Keycloak staging still fires), assurance upgrades, recent decisions; per-queue degradation never 500s; tshepo-authz V032 seeds + spec doc | varapi 197, tuso 127, authz 129, BFF 847 — independently re-run |
| Workforce Intake journey | `/admin/workforce-intake` six-step wizard → BFF `/internal/v1/workforce-intake` (upload≤2000 rows → mapping/validation → dedupe + identity match → approve → per-row idempotent execute: varapi issuance / reuse-by-anchor, vashandi single-row bridge, invitations, `BLOCKED_MISSING_CONTACT` honesty) → wgv V011 batch pipeline (stage FSM, execution columns); authz V033 (renumbered from V032 collision at merge) | wgv 107, authz 129, BFF 860 — independently re-run |
| Activation letters | Print-CSS `/registry-admin/activation-letter` (Provider ID, activation link + one-time code, expiry, helpdesk); tested invariant: **never** contains passwords or clinical content | component tests green |
| Channel honesty + secrets | `GET /internal/v1/channels/status` (+BFF passthrough + comms-ops card "stub — not delivered"); helm secretEnv for SMTP/SMS from `impilo-app-secrets`; bootstrap seeds operator-provided placeholder keys (never randomised) | ChannelStatusControllerTest 3 green; yaml validated |
| Golden-journey harness | Playwright `journeys` project — honest login via real `/auth/login` per persona; 10-point acceptance checklist (access, actionable, form, observed 2xx save, state change, cross-user, notification, audit, resume, logical end) with explicit N/A or the run fails; specs: start-menu-discoverability (6 personas), fundo-author→learner (cross-user + logout/resume), clinical-day (clerk→nurse→doctor→chart), diagnostics-imaging (order→worklist→release→inbox + **hard notification assert**); runner `scripts/e2e/run-golden-journeys.sh` (+ proven telehealth two-party spec) with evidence to `reports/journeys/` | 14 tests discovered; tsc clean; run against live estate is the acceptance step |

## 3. Honest gaps / deferrals (tracked, not smoothed)

- **Deploy + live proof not yet run** — blocked at report time by an active concurrent session in the
  shared checkout (deploy-lane law). Runbook in §4.
- Consolidated Trust Console **Invitations tab** (compose wgv rows + Keycloak activation status +
  resend in one place): deferred — per-batch invitation state and resend live in the intake tracker.
- **Governance-intake journey spec** (claim → IATG approve → intake execute in the browser): add
  after first live run of the new surfaces.
- Intake identity matching: no national-ID resolution exists platform-wide (tshepo-identity resolves
  HEALTH_ID/COUNCIL_NUMBER/…; EMAIL resolution upstream is a documented TODO) — rows match by
  healthId → councilNumber → email; NID-only rows honestly NO_MATCH and mint a new anchor.
- Intake facilityCode→UUID lookup (TUSO) missing — non-UUID codes yield org membership without a
  facility assignment (no silent guessing).
- tuso facility-admin appointments expose approve only — console surfaces an honest 400 for reject.
- Email/SMS remain **log-mode until an operator provides credentials** (by design); the admin panel
  says so explicitly. HL7/FHIR/MWL interop flags remain off (config posture, not build gap).
- Learner/author-facing audit surfaces (Fundo) remain outbox-only; recorded as N/A-with-reason in
  the acceptance checklists.
- HIE_ADMIN policy rules are seeded and become active when the persona pack's realm reconcile runs
  on the estate.

## 4. Deploy + prove runbook (task #9)

Pre-condition: **no other session active in this checkout** (deploy-lane law).

```bash
# 1. gates for THIS SHA (VM-local; hairpin-safe reachability is built in)
bash scripts/pipeline/run-local-quality-gates.sh
# 2. restore gate-regenerated noise (docs/, reports/product, classification, registry-maturity)
# 3. full authorized deploy — code changed, so NO skip-build/skip-import/no-digest-pin/skip-push
bash scripts/deploy/manual-authorized-preview-deploy.sh
# 4. seed + verify personas on the estate
bash scripts/operator/seed-persona-truth-pack.sh
# 5. golden journeys against the live estate (evidence to reports/journeys/)
bash scripts/e2e/run-golden-journeys.sh
```

Acceptance = all five green **plus** `curl http://127.0.0.1/health/version` reporting the deployed
SHA. A journey failure at step 5 is a wave finding to fix, not a spec problem — that is the point.
