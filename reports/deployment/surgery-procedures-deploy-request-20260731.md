# Deploy request — Surgery + Clinical Procedures (theatre clinician parity)

**Date**: 2026-07-31  
**Tip SHA**: `3c87c0f37`  
**Branch**: `claude/staging-ux-orchestration-remediation-Yypyl`  
**Requested by**: Wave D theatre / course-of-care clinician parity close  
**Decision required from**: product owner  

## What is being asked for

`AUTHORIZE DEPLOY WITH VM GATES` — authorise a Dev Preview Sandbox deploy of this tip and a
smoke against surgery + theatre clinician surfaces over real HTTP. **Do not deploy without that
phrase.** Local quality gates must be re-run at the pushed tip before deploy scripts will allow
it.

## Tip and what changed since the 2026-07-30 request

Prior request: `reports/deployment/surgery-procedures-deploy-request-20260730.md` (completion
wave). This refresh adds clinician parity that landed after that tip:

| Commit | Change |
|---|---|
| `9908827d1` | Theatre specimen custody + implant lifecycle UI on the case surface |
| `3ae3ff04c` | Inpatient theatre site/side confirmation |
| `e00d64d70` | Surgery course-of-care panels on the episode page |
| `2bac7f83a` | Surgical site-marking body maps on the theatre case |
| `3c87c0f37` | BFF proxy for theatre site-side confirmation |

Also already on the stack (pre-tip, for context): `tshepo-authz V304` + BFF for course-of-care
families — closes the SB-3 BACKEND-INTERNAL gap for prehab / complications / longitudinal /
follow-up / waitlist revalidation.

## Verification already run (not a substitute for gates or browser smoke)

- Vitest: surgery `page.test.tsx`, `TheatreSpecimenPanel`, `TheatreCommoditiesPanel`,
  `TheatreSiteMarkingPanel` — **28/28 PASS**
- `tsc --noEmit` in `ui/one-ui-shell` — **PASS**
- Optional Java: `ProcedureSiteSideConfirmTest` (4), `TheatreControllerTest` (31),
  `SurgeryReachabilityRouteShapeTest` (20) — **PASS**
- VM quality gates: pending at this tip (run after push; report PASS/FAIL before deploy)

## Still unmet until deploy

No browser has loaded `/work/clinical/surgery` or the theatre case parity panels against a live
BFF. No Envoy/TSHEPO evaluation of these rows against real trust headers. Treat REACHABLE as
wiring-proven, not production-proven.

## Authorisation

Per `.cursor/rules/ci-feedback-and-manual-deploy.mdc`, type exactly:

`AUTHORIZE DEPLOY WITH VM GATES`

No kubectl/helm/preview-deploy will be attempted without it.
