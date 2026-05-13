# Retirement telemetry signals

> **Purpose.** Names the telemetry signals each [`retirement-readiness-ledger.md`](retirement-readiness-ledger.md) entry depends on, so that a future evidence-gathering batch can wire dashboards / queries / CI checks against the right sources rather than guessing.
>
> **Scope.** This document is **definitional**, not operational. It does not configure any dashboard, write any code, or fire any query. It only names the signals so the ledger has somewhere to link to.

## 1. Signal catalogue

### SIDECAR_UI

**What it measures.** HTTP request count to any route under the sidecar UI's base path.

**Applies to.** RR-01 (`ui/mushex-finance-console`), RR-02 (`ui/mushex-ops-console`), RR-03 (`ui/mushex-payer-portal`).

**Source.**
- Production / staging ingress logs filtered by host header and path prefix.
- Browser-side analytics, if the sidecar emits any (it likely does not; sidecars do not have parity with `one-ui-shell` telemetry).

**Aggregation window.** Daily count, rolling **30-day** window must be `0`.

**Threshold for `evidence-clean`.** `0` request-per-day for **30 consecutive days** across all environments where the sidecar is deployed.

**False-positive risk.** Healthchecks, robots, and uptime probes can inflate the count above zero without representing real user traffic. The query must filter on a user-agent allowlist (browser user-agents only) and exclude known probe IPs.

**Where to wire it.** Each sidecar will need a per-app dashboard panel that surfaces a 30-day daily-count chart. Today there is no such panel; the dashboard wiring is a follow-on operational task explicitly out of scope for this batch.

### LEGACY_BFF_ROUTE

**What it measures.** Inbound HTTP request count to a specific Experience BFF route or controller group that has been superseded by a canonical replacement.

**Applies to.** RR-06 (legacy mobile-citizen wallet routes — wellness-proxy whitelist + `CitizenMyLifeController` wallet endpoints).

**Source.**
- Experience BFF access logs (Spring access log appender) filtered by request path.
- A future per-route counter exposed via `/actuator/metrics` (not in scope here).

**Aggregation window.** Daily count, rolling **60-day** window must be `0`, OR the mobile-build adoption telemetry must confirm that the slowest-rolling mobile build has been retired for **at least 30 consecutive days**.

**Threshold for `evidence-clean`.** Both: (a) daily inbound count to the deprecated path is `0` for 60 consecutive days, AND (b) the mobile rollout doctrine's "supported mobile-build window" has elapsed since the canonical replacement was first shipped.

**False-positive risk.** Local development / smoke tests / integration tests can hit the legacy path. The query must scope to production traffic only.

**Where to wire it.** The Experience BFF already emits access logs in a structured format compatible with Loki / Datadog / Splunk. A specific log-query template will be defined when this signal is operationalised; today, the template is owned by infrastructure operations.

### LEGACY_WEB_SHELL

**What it measures.** HTTP request count to any route served by a legacy web shell.

**Applies to.** RR-04 (`ui/experience`), RR-05 (`ui/ehr`).

**Source.** Same as `SIDECAR_UI`.

**Aggregation window.** Daily count, rolling **30-day** window must be `0`.

**Threshold for `evidence-clean`.** Same shape as `SIDECAR_UI`.

**False-positive risk.** Same as `SIDECAR_UI`, plus: links inside emails / external bookmarks can keep low-volume traffic alive for months. The query must distinguish "human navigation" from "stale link follow" if possible; failing that, the threshold must be `0` for an extended window before retirement.

**Where to wire it.** Same as `SIDECAR_UI`.

### CANONICAL_PARITY

**What it measures.** Whether the canonical replacement is at functional parity with the deprecated artefact, measured by a fresh parity audit walking each user-visible feature of the deprecated artefact and locating its canonical equivalent.

**Applies to.** All ledger entries that name a canonical replacement.

**Source.**
- A written parity audit document for the specific (deprecated, canonical) pair.
- Optionally, a CI check that asserts canonical-side route presence (e.g. `route-parity-check.mjs`).

**Aggregation window.** N/A — this is a one-shot check at retirement time.

**Threshold for `evidence-clean`.** Every feature of the deprecated artefact must either have a documented canonical replacement OR be explicitly recorded as "intentionally not migrated, with reason and approval".

**False-positive risk.** A parity audit can miss subtle feature gaps. Pair it with `SIDECAR_UI` / `LEGACY_WEB_SHELL` telemetry — if traffic to a sidecar drops to zero without a canonical replacement, the more likely explanation is that the sidecar is unused, not that parity has been silently achieved.

**Where to wire it.** Parity audits live in `docs/audits/`. The most recent example is the COSTA / MusheX experience-layer wiring audit; new ones should follow its row-per-feature shape.

### CI_BUILD_REMOVED

**What it measures.** Whether the deprecated artefact has been removed from CI pipelines and is no longer built, packaged, or published.

**Applies to.** Any ledger entry whose artefact is a buildable module (sidecar UI, web shell, mobile route family).

**Source.**
- `.github/workflows/**` files in this repository.
- Any build-matrix configuration.

**Aggregation window.** N/A — point-in-time check.

**Threshold for `evidence-clean`.** No CI workflow names the artefact in `paths`, `paths-ignore`, `matrix`, or any `run:` step. No published artefact registry contains a newer-than-retirement-date build for the artefact.

**False-positive risk.** A workflow that is `if: false` or otherwise gated may still appear in greps without actually running.

**Where to wire it.** A simple `rg`-equivalent search across `.github/workflows/` plus a sanity walk-through of any artefact registry.

## 2. Signal-to-criterion mapping

| Ledger entry | Required signals |
| ------------ | ----------------- |
| RR-01 | `SIDECAR_UI` ∧ `CANONICAL_PARITY` ∧ `CI_BUILD_REMOVED` |
| RR-02 | `SIDECAR_UI` ∧ `CANONICAL_PARITY` ∧ `CI_BUILD_REMOVED` |
| RR-03 | `SIDECAR_UI` ∧ `CANONICAL_PARITY` ∧ `CI_BUILD_REMOVED` |
| RR-04 | `LEGACY_WEB_SHELL` ∧ `CANONICAL_PARITY` ∧ `CI_BUILD_REMOVED` |
| RR-05 | `LEGACY_WEB_SHELL` ∧ `CANONICAL_PARITY` ∧ `CI_BUILD_REMOVED` |
| RR-06 | `LEGACY_BFF_ROUTE` ∧ mobile-build-adoption-window-elapsed ∧ `CANONICAL_PARITY` |
| RR-07 | TBD on confirming the artefact exists. |

## 3. What this document does not do

- It does not configure any Datadog / Splunk / Loki dashboard.
- It does not wire any `/actuator/metrics` counter.
- It does not run any parity audit.
- It does not modify CI pipelines.

All four of those operational steps are intentionally outside the scope of an "audit + definitional" batch. They are the natural follow-on Phase 7 slices.

## 4. Suggested follow-on Phase 7 slices

| Slice id | What | Effort | Risk |
| -------- | ---- | ------ | ---- |
| 7B | Wire production dashboards for the three `SIDECAR_UI` signals (RR-01/02/03). | Operational — touches dashboard tooling, not the repository. | Low (read-only telemetry). |
| 7C | Wire production dashboards for the two `LEGACY_WEB_SHELL` signals (RR-04/05). | Operational. | Low. |
| 7D | Add a per-route inbound counter on the legacy mobile-citizen wallet paths in Experience BFF (`LEGACY_BFF_ROUTE` for RR-06). | One small additive code change to log/metric the legacy paths explicitly. | Low — log/metric additive only. |
| 7E | Run fresh parity audits per ledger entry. | One audit document per entry. | Low. |
| 7F | Add a CI guard that fails the build if a new file is added under any deprecated folder. | One small `.github/workflows` check. | Low; protects the ledger from drift. |

Each slice is independently scoped and can land in its own batch without coupling to the others.
