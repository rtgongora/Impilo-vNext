# Retirement-readiness ledger

> **Purpose.** A single canonical place that tracks every component, page, route, or endpoint in the Impilo vNext repository that has been **explicitly deprecated** (via a `DEPRECATED.md`, a doctrine note, or an audit gap) but has **not yet been deleted**, together with the evidence still needed before retirement can proceed safely.
>
> **Scope.** This ledger is the merge point for "things flagged for retirement". It does **not** retire anything; retirement itself is always a separate, explicit batch with a code change, a CI-checked test plan, and a doctrine update. This ledger only records what is flagged, what blocks retirement, and what evidence has already been gathered.

| Field | Value |
| ----- | ----- |
| Status | Active (Phase 7 follow-on). Ledger + telemetry definitions + parity audit + CI/file-growth guard in place. |
| Companion | [`telemetry-signals.md`](telemetry-signals.md) — defines the telemetry signals each entry must satisfy before retirement. |
| Predecessor audit | [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) (audit gaps G-3, G-6 in particular). |

## 1. Why a central ledger

Until Phase 7, each deprecation lived in its own `DEPRECATED.md` (per sidecar) or as a single row of an audit document. That meant:

- There was no one place to see *all* outstanding retirements together.
- Each `DEPRECATED.md` repeated retirement criteria language inconsistently.
- The "what telemetry do I need before deleting this?" question had no canonical answer.
- Reviewers couldn't tell, at a glance, whether a deletion PR was safe to merge.

The ledger fixes this by:

- Naming every outstanding deprecation with a stable **ledger id** (RR-NN).
- Recording, per entry, the **canonical replacement**, the **retirement criteria**, and the **current evidence**.
- Cross-linking every entry back to its `DEPRECATED.md` and the audit gap that opened it.

## 2. Status taxonomy

Each ledger entry uses one of the four statuses below:

| Status | Meaning |
| ------ | ------- |
| `flagged` | Marked as deprecated; retirement criteria not yet defined or measured. |
| `awaiting-evidence` | Retirement criteria defined; no telemetry collected yet, or evidence is partial. |
| `evidence-clean` | Evidence collected; the criteria are met; retirement can be **scheduled** but has not yet been executed. |
| `retired` | Retirement has been executed; the entry is kept here as a historical anchor for the audit trail. |

Promotion from `awaiting-evidence` to `evidence-clean` requires linking the actual evidence (e.g. a dashboard screenshot, a `kubectl logs` excerpt, a Datadog metric query, or a CI test run) inside the entry. Promotion to `retired` requires linking the PR that removed the artefact.

## 3. Ledger entries

### RR-01 — `ui/mushex-finance-console` (sidecar UI)

| Field | Value |
| ----- | ----- |
| Status | `retired` |
| Source of truth | [`ui/mushex-finance-console/DEPRECATED.md`](../../ui/mushex-finance-console/DEPRECATED.md) |
| Audit gap | G-6 in [`costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) |
| Canonical replacement | `ui/one-ui-shell` finance pages: `/finance/settlements`, `/finance/reconciliation`, `/finance/refunds`, `/finance/ledger`, `/finance/costa`, `/finance/mushex-platform` |
| Retirement criteria | (a) Telemetry confirms zero load of any `/mushex-finance-console/**` route for **30 consecutive days** in any environment where the sidecar is deployed. (b) Build pipelines for the sidecar are removed from CI and no other repository depends on its artefacts. (c) All canonical replacement pages are at parity with the sidecar's last-shipped behaviour. |
| Current evidence | Wave 6 program (2026-06-08): canonical parity confirmed in [`phase-7-retirement-parity-audit.md`](../audits/phase-7-retirement-parity-audit.md); not in preview helm; CI/full-boot build removed — see [`wave-6-sidecar-retirement-record.md`](../product-truth/wave-6-sidecar-retirement-record.md). |
| Blockers | None — folder retained with `DEPRECATED.md` for audit trail; physical deletion deferred. |

### RR-02 — `ui/mushex-ops-console` (sidecar UI)

| Field | Value |
| ----- | ----- |
| Status | `retired` |
| Source of truth | [`ui/mushex-ops-console/DEPRECATED.md`](../../ui/mushex-ops-console/DEPRECATED.md) |
| Audit gap | G-6 |
| Canonical replacement | `ui/one-ui-shell` finance + admin pages, including `/finance/mushex-platform`, `/finance/payer-ops`, `/finance/reconciliation`, `/admin/audit/**`. |
| Retirement criteria | (a) Telemetry confirms zero load of any `/mushex-ops-console/**` route for **30 consecutive days** in any environment where the sidecar is deployed. (b) Build pipelines for the sidecar are removed from CI. (c) Canonical replacement parity confirmed. |
| Current evidence | Wave 6 program (2026-06-08): canonical parity + not in preview helm + CI build removed — see [`wave-6-sidecar-retirement-record.md`](../product-truth/wave-6-sidecar-retirement-record.md). |
| Blockers | None — folder retained with `DEPRECATED.md` for audit trail; physical deletion deferred. |

### RR-03 — `ui/mushex-payer-portal` (sidecar UI)

| Field | Value |
| ----- | ----- |
| Status | `retired` |
| Source of truth | [`ui/mushex-payer-portal/DEPRECATED.md`](../../ui/mushex-payer-portal/DEPRECATED.md) |
| Audit gap | G-6 |
| Canonical replacement | `ui/one-ui-shell` finance pages: `/finance/payer-claims`, `/finance/payer-ops`, `/finance/refunds`. |
| Retirement criteria | (a) Telemetry confirms zero load of any `/mushex-payer-portal/**` route for **30 consecutive days** in any environment where the sidecar is deployed. (b) Build pipelines for the sidecar are removed from CI. (c) Canonical replacement parity confirmed. |
| Current evidence | Wave 6 program (2026-06-08): canonical parity + not in preview helm + CI build removed — see [`wave-6-sidecar-retirement-record.md`](../product-truth/wave-6-sidecar-retirement-record.md). |
| Blockers | None — folder retained with `DEPRECATED.md` for audit trail; physical deletion deferred. |

### RR-04 — `ui/experience` (legacy web shell — pre-`one-ui-shell`)

| Field | Value |
| ----- | ----- |
| Status | `flagged` |
| Source of truth | [`ui/experience/DEPRECATED.md`](../../ui/experience/DEPRECATED.md) |
| Audit gap | Doctrine reference: `docs/doctrine/health-os-doctrine.md`. No active audit gap row, but every canonical page should now live in `one-ui-shell`. |
| Canonical replacement | `ui/one-ui-shell` (entire shell). |
| Retirement criteria | (a) No reverse imports from `ui/one-ui-shell` to `ui/experience`. (b) Telemetry confirms zero traffic to any `ui/experience` page in production for **30 consecutive days**. (c) Build artefacts are removed from any deployable target. |
| Current evidence | Query templates available in [`telemetry-query-recipes.md`](./telemetry-query-recipes.md); parity evidence in [`docs/audits/phase-7-retirement-parity-audit.md`](../audits/phase-7-retirement-parity-audit.md). |
| Blockers | (1) telemetry source missing; (2) explicit per-page parity walk-through has not been run. |

### RR-05 — `ui/ehr` (legacy clinical web shell — pre-`one-ui-shell`)

| Field | Value |
| ----- | ----- |
| Status | `retired` |
| Source of truth | [`ui/ehr/DEPRECATED.md`](../../ui/ehr/DEPRECATED.md) |
| Audit gap | Doctrine reference: `docs/doctrine/health-os-doctrine.md`. |
| Canonical replacement | `ui/one-ui-shell` `/ehr/**` pages. |
| Retirement criteria | Same shape as RR-04. |
| Current evidence | Wave 6 program (2026-06-08): canonical parity + not in preview helm + CI build removed — see [`wave-6-sidecar-retirement-record.md`](../product-truth/wave-6-sidecar-retirement-record.md). Sidecar ledger already marked `retired sidecar path`. |
| Blockers | None — folder retained with `DEPRECATED.md` for audit trail; physical deletion deferred. |

### RR-06 — Legacy mobile-citizen wallet routes (wellness-proxy whitelist + `CitizenMyLifeController` wallet endpoints)

| Field | Value |
| ----- | ----- |
| Status | `awaiting-evidence` |
| Source of truth | Stage 3.4B in `docs/doctrine/mushex-gateway-neutrality.md` and audit gap G-3 in [`costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md). |
| Audit gap | G-3 (retirement tail) |
| Canonical replacement | `/internal/v1/wallet/me` and `/internal/v1/wallet/me/transactions` on the Experience BFF (wired into `apps/mobile/citizen-app/src/services/walletService.ts` in Stage 3.4B). |
| Retirement criteria | (a) Mobile-citizen app analytics confirm zero calls to the legacy wellness-proxy wallet path **and** to the legacy `CitizenMyLifeController` wallet endpoints for **at least the duration of the slowest documented mobile-build adoption window** (typically 60 days, but the figure must come from the mobile rollout doctrine, not from this ledger). (b) The `walletService.ts` retirement test asserts no fallback to legacy paths. (c) Server-side BFF logs confirm no inbound traffic on the legacy paths. |
| Current evidence | Stage 3.4B mobile tests for canonical wallet-plane usage plus explicit metric instrumentation in Experience BFF (`impilo.legacy.route.requests`, `route_family=mobile_citizen_wallet`) added in Phase 7D. |
| Blockers | (1) mobile-build adoption telemetry source not yet linked; (2) dashboard/query evidence snapshots for the new counter are not yet attached. |

### RR-07 — `costa-console` sidecar (if/when it is identified)

| Field | Value |
| ----- | ----- |
| Status | `flagged` |
| Source of truth | `docs/architecture/vnext-component-catalog.md` (Section 2.4) lists `costa-console` as a deprecated finance UI. No standalone `DEPRECATED.md` exists yet because the folder may not be present in this repository. |
| Audit gap | None today. |
| Canonical replacement | [`/finance/costa`](../../ui/one-ui-shell/src/app/finance/costa/page.tsx) |
| Retirement criteria | If the folder exists in this repository, add a `DEPRECATED.md` matching RR-01..RR-03 first, then apply RR-01-style criteria. If it does not exist as a deployable artefact, this entry can be promoted to `retired` immediately with a link to the catalog row. |
| Current evidence | Pending verification that the folder is or is not present. |
| Blockers | (1) verify presence; (2) if present, write a `DEPRECATED.md` and define retirement criteria. |

## 4. Process for adding a new entry

1. Open a new row at the bottom of § 3 with the next free `RR-NN` id.
2. Link the entry to its `DEPRECATED.md` (or, if a `DEPRECATED.md` does not exist yet, write one first).
3. State the **canonical replacement** explicitly. If there is no canonical replacement, the entry should remain in `flagged` status and the deprecation itself is suspect — escalate before retirement.
4. State the **retirement criteria** in observable terms (telemetry, CI checks, parity audits). Each criterion must be something a reviewer can verify by following a link.
5. Link any **current evidence** (or leave the field blank with `Not collected yet`).
6. List any **blockers** that prevent advancing the status.

## 5. Process for advancing an entry's status

| Transition | Required artefacts |
| ---------- | ------------------ |
| `flagged` → `awaiting-evidence` | Retirement criteria are written in observable terms; canonical replacement is named. |
| `awaiting-evidence` → `evidence-clean` | Every criterion has a link to evidence (dashboard, log query, CI run, parity audit). The companion `telemetry-signals.md` document explains which signal corresponds to each criterion. |
| `evidence-clean` → `retired` | A PR that deletes the artefact is linked here, and the corresponding audit gap row is moved to "Closed". |

## 6. Cross-references

- [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) — audit gaps referenced by ledger entries.
- [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) — doctrine deferral table; rows aligned with G-3, G-6.
- [`docs/architecture/vnext-component-catalog.md`](../architecture/vnext-component-catalog.md) — inline deprecation markers added in Phase 1.
- [`telemetry-signals.md`](telemetry-signals.md) — defines the telemetry signals each ledger criterion depends on.
- [`telemetry-query-recipes.md`](telemetry-query-recipes.md) — copy-ready query templates for `SIDECAR_UI` and `LEGACY_WEB_SHELL`.
- [`docs/audits/phase-7-retirement-parity-audit.md`](../audits/phase-7-retirement-parity-audit.md) — first parity sweep evidence for RR-01..RR-07.
- The per-sidecar `DEPRECATED.md` files (`ui/mushex-finance-console`, `ui/mushex-ops-console`, `ui/mushex-payer-portal`, `ui/experience`, `ui/ehr`).
