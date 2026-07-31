# Deploy request — Surgery + Clinical Procedures programme

**Date**: 2026-07-30
**Requested by**: completion wave (see `docs/registry/iatg-surgery-procedures-leases.md` §29)
**Branch**: `claude/staging-ux-orchestration-remediation-Yypyl`
**Decision required from**: product owner

## What is being asked for

Authorisation to deploy this branch to the Dev Preview Sandbox and run a smoke test against the
surgery and clinical-procedures surfaces over real HTTP.

## Why it matters more than a routine deploy

**This programme has never been deployed, and no request it serves has ever crossed Envoy.** That
has been true through Phase 0, Wave P-R, all sixteen Phase P waves, all four Phase S waves, six
backlog-clearing batches and now the completion wave. Wave P-R's own definition of done — a
request reaching a handler through the real estate — has been unmet from the day it was written.

Everything the programme has shipped is verified by unit tests, by route-shape tests asserted
against the real ext_authz derivation logic, and by rigs that run each service against a real
Postgres in Docker. That is genuine verification and it has caught real defects, including a total
outage of theatre intake that had been live for five waves. **It is not the same as the software
running.** Nothing here has been exercised through Envoy, through TSHEPO's policy decision point
with real trust headers, through the experience BFF as a running process, or in a browser.

The gap this deploy closes is specific:

- **ext_authz has never evaluated these policy rows against a real request.** The rows in
  tshepo-authz V300–V303 are asserted correct by reading the PDP's own derivation function in a
  unit test. Whether a real request carrying real trust headers produces the resource type, action
  and role those rows expect has not been observed once.
- **The BFF proxy has never forwarded a live call.** `SurgeryController` and `ProceduresController`
  are proven as pass-throughs against a stub client.
- **No browser has loaded `/work/clinical/surgery` or `/work/clinical/procedures`.** The pages
  render correctly under jsdom in Vitest. That is not the same as rendering in a browser against a
  live BFF.
- **No migration in the programme has run against a preview database.** They have run against
  throwaway Postgres containers in rigs, which is a good proxy and not the same thing.

## What is being deployed

Migrations that will apply on first boot:

| Service | Migrations |
|---|---|
| `surgery-service` | V001–V012 (the whole service; never deployed) |
| `inpatient-service` | V300–V305 |
| `tshepo-authz-service` | V300–V303 |
| `clinical-knowledge-platform-service` | V300 |
| `procedures-service` | the whole service; never deployed |

One of these is worth naming explicitly. **CKP V300 loads 147 surgical clinical-decision-support
rules.** They are deliberately incapable of driving care, by three independent properties, each
asserted per-row by `SurgicalSeedRuleContentTest`: their logic columns are NULL so nothing can
fire; their `approval_status` is `ENGINEERING_SEED` with `adaptation_authority` of
`PENDING_MOHCC_RATIFICATION`; and every row is non-interruptive with override allowed. Their
`effective_start` is `9999-01-01`. They are seeded content awaiting MoHCC ratification, and they
must not be described to any clinical audience as decision support that is in force.

## Requested smoke test

1. `/health/version` on `surgery-service` and `procedures-service` matches the deployed commit.
2. Flyway reports the migrations above as applied, with no failed or pending entries.
3. One authorised request per new route family through Envoy with real trust headers — open a
   surgical episode, read it, add a joining specialty, reopen an operated episode — each returning
   the expected 2xx.
4. One **unauthorised** request per family, from a cadre with no ALLOW row, returning a genuine
   deny rather than a pass-through. A reachability wave that only tests the happy path proves half
   of nothing.
5. `/work/clinical/surgery` loaded in a browser: episode list, specialties panel, reopen panel,
   decision panel with the MDT forum.
6. The failure paths, deliberately: stop surgery-service and confirm the UI renders "could not
   read" rather than an empty list. This is the guarantee the whole programme's client-honesty work
   exists to provide, and it has never been observed against a real outage.

## What would make this a bad time to deploy

Nothing known. The ten theatre rigs are green at baseline, the local quality gates pass, and the
backend-frontend parity guard passes. If the preview stack is mid-migration for another lane, that
is the only reason to wait.

## Authorisation

Per `.cursor/rules/ci-feedback-and-manual-deploy.mdc`, a preview deploy needs explicit product
owner approval, and a deploy on VM gates alone needs the phrase
`AUTHORIZE DEPLOY WITH VM GATES`. No deploy will be attempted without it.
