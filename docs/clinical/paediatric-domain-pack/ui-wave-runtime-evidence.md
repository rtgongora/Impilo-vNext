# Paediatric UI wave — runtime evidence, and what is NOT proven

**Recorded 2026-08-02.** This file exists because the wave's standing requirement was browser
proof against `impilo-full-preview`, and that requirement was **not fully met**. What follows
separates what was verified on the live estate from what was not, so nobody reads the wave as
proven when part of it is not.

## Deployed

| | |
|---|---|
| `experience-bff` | `127.0.0.1:5000/impilo/experience-bff@sha256:bc90e222…4b094cc985` |
| `one-ui-shell` | `127.0.0.1:5000/impilo/one-ui-shell@sha256:31b659c5…928a170cbf` |

Both rolled out `1/1 Running`, 0 restarts.

**Neither image was built by the normal pipeline, and that is worth knowing.** This host has no
egress to Docker Hub — `eclipse-temurin:21-jre` and `node:20-bookworm-slim` both time out — so the
multi-stage builds cannot run. Each artefact was therefore built locally and layered onto the image
that was already deployed:

- **BFF**: `mvn -o -DskipTests package`, then the jar copied over the running image's `/app/app.jar`.
  Everything else in the image is byte-identical to what was already running.
- **Shell**: `next build` locally with the same build-time `API_GATEWAY_URL`/`BFF_URL` the Dockerfile
  bakes in (the rewrites are compiled into `routes-manifest.json`, so this matters), then the
  application tree copied over. `/app/node_modules` was kept from the base because these commits
  change no dependency — `package.json` and `package-lock.json` are untouched — and the local build
  could not produce a traced `node_modules` of its own.

## Verified on the live estate

**The deployed BFF jar is byte-identical to the local build.**

```
local jar:  9b28a3dbce166b2aea795bb81f3da6696b9a7d5d0d5133909213c197445d7393
deployed:   9b28a3dbce166b2aea795bb81f3da6696b9a7d5d0d5133909213c197445d7393
```

and that jar was confirmed to contain
`BOOT-INF/classes/…/controller/PaediatricDecisionSupportController.class`. This is the
stale-jar trap this estate has been bitten by before, closed by measurement rather than assumed.

**The deployed shell bundle contains the new surfaces.** Inside the running pod:

- `/app/one-ui-shell/.next-build/server/app/ehr/[patientId]/imnci` exists — the route did not exist
  before this wave.
- The growth-interpretation honesty strings (`interpretation_blocked`, "Growth not interpreted")
  appear in the compiled `paediatrics`, `immunizations` and `growth-chart` page bundles.

## NOT proven, and why

**No surface was driven end to end in a browser by a clinician.**

The only governed preview test identity is `preview.test.citizen`, and
`docs/security/trust-audit/checkpoint-3/PREVIEW_TEST_IDENTITY.md` states its scope explicitly:
realm roles `CITIZEN` + `default-roles-impilo`, and *"Facility / workforce / clinical / regulatory /
finance / admin / platform authority: **none**"*. Every surface in this wave is a clinician EHR
surface. That identity cannot reach them by design, and it should not be widened to make a test
pass — the point of a scoped test identity is that it is scoped.

In the run that was attempted, the login did not establish a session at all
(`{"error":{"code":"NO_ACTIVE_SESSION"}}`, no cookies set). **That failure is not specific to these
routes**: from the same page context, the long-standing `/internal/v1/clinical/rules/evaluate` and
`/internal/v1/immunizations` returned exactly the same 401. Preview enforces real authentication —
`IMPILO_SECURITY_ALLOW_ANONYMOUS=false`, `AUTH_FALLBACK_ENABLED=false` — with no test bypass.

Attempts stopped at three of the realm's five-failure brute-force lockout rather than continuing to
guess.

**A routing check was attempted and discarded as worthless.** `/ehr/x/imnci` returns 307 through the
real ingress — but so does `/ehr/x/no-such-page-xyz`, because the auth middleware redirects
everything. The negative control showed the check could not distinguish a real route from a
nonexistent one, so it proves nothing and is recorded here only so nobody repeats it.

## The spec is written and will pass the moment an identity exists

`ui/one-ui-shell/e2e/journeys/paediatric-decision-support.journey.spec.ts` drives all four engines
and both pages through a real session and asserts the properties that matter — an `INDETERMINATE`
row keeping its `missing_inputs`, a child with nothing assessed reporting `incomplete`, a
`BLOCKED_BY_INTERVAL` dose carrying its gate-open date and staying out of the giveable set, a single
growth contact reporting not-assessable, and a missing date of birth returning 400 rather than 502.
It skips when `PREVIEW_TEST_USERNAME`/`PREVIEW_TEST_PASSWORD` are absent, so it can never pass
vacuously.

Its first version had the defect this codebase keeps finding: test 0 asserted only that the browser
landed back on the app hostname, and **passed while no session existed**. It now asserts
`/internal/v1/auth/oidc/session` returns 200, so a failed login fails the spec rather than letting
the six tests after it report a misleading result.

## What would close this

A preview test identity with clinical scope — a provider with a facility affiliation and an active
role — is a governed trust-lane action, not one to take unilaterally on a shared estate. Once one
exists:

```bash
PLAYWRIGHT_SKIP_WEBSERVER=1 \
PLAYWRIGHT_HOST_RESOLVER_RULES="MAP impilo.mohcc.gov.zw 10.50.1.67" \
PLAYWRIGHT_BASE_URL="https://impilo.mohcc.gov.zw" \
PREVIEW_TEST_USERNAME=… PREVIEW_TEST_PASSWORD=… \
npx playwright test --project=journeys e2e/journeys/paediatric-decision-support.journey.spec.ts --workers=1
```

The `--host-resolver-rules` mapping is required: the public hostname is unreachable from inside the
preview VM (hairpin NAT), which is why this spec lives under `e2e/journeys/` — that Playwright
project is the one that applies it.

## Standing caveat, unchanged by this wave

Every threshold, cut-off, dose and classification behind these surfaces is `ENGINEERING_SEED` /
`PENDING_MOHCC_RATIFICATION`. The UI now displays that provenance on each panel. None of it should
drive care until MoHCC and a paediatric specialist have signed it off.
