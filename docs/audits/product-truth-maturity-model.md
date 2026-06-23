# Product Truth — Honest Maturity Model

_Established: Wave 2 (intake/product-truth-scanner-honesty), 2026-06-23._

## Why this exists

The product-truth scanner previously declared a service `real` when **≥6 of its
file-existence dimensions were present** (`overallProductStatus`,
`product-truth-gaps.mjs`). That is a *file-existence* signal — it cannot tell a
real capability from a fixture-backed shell, a placeholder controller, or an
in-memory store masquerading as persistence. The result was a report claiming
`67/67 user-facing real, 0 gaps, phase6 92/92` while product surfaces still
rendered hardcoded data and a wallet service derived crypto keys from a
placeholder.

The maturity model adds an **honest axis** on top of the (retained) file-existence
`productStatus`, so the report stops asserting proof it never gathered.

## The labels

| Maturity | Meaning |
|----------|---------|
| `REAL_PROVEN` | **Reserved.** Only emitted when a runtime/test **probe-evidence artifact** is supplied (`svc.probeEvidence.passed`). The static scan **never** emits this on its own. |
| `REAL_CODE_NOT_PROBED` | Code is present and wired across dimensions, but nothing proves it runs. This is the ceiling a static scan can claim. |
| `PARTIAL` | Some dimensions present, others thin/absent. |
| `FIXTURE_BACKED` | A mock/stub/hardcoded-data/in-memory/placeholder hit was found in a product path — it cannot be called real regardless of file counts. |
| `BACKEND_ONLY` | Backend present, no user-facing surface where one is expected. |
| `UI_ONLY` | UI surface with no detected backend. |
| `INTERNAL_ONLY` | Platform/ops service, no actor-facing UI required. |
| `DEFERRED_WITH_ADR` | Deprecated / deferred with a recorded decision. |
| `UNKNOWN` | Insufficient signal. |

## What the scanner now detects (beyond file existence)

- **Component-level fixtures** — `scanMockStubHits` + `scanHardcodedCollections` now
  run over the components a page mounts (transitively), not just the `page.tsx`.
  This is what catches `BillingPanel`/`StockManagementPanel` financial/stock
  fixtures that the page-only scan missed.
- **Hardcoded data collections** — `const UPPER_SNAKE = [{…data row…}]` that is
  `.map`-rendered, where the record carries transactional/temporal/clinical fields
  (`amount`, `invoice`, `date`, `reference`, …). Presentational config/taxonomy
  (`EHR_ACTIONS`, `SEVERITY_LEVELS`, `PROVINCES`) is deliberately **excluded** —
  precision over recall to keep the signal trustworthy.
- **In-memory stores** — a `*Store.java` class whose state lives in a concurrent
  in-memory collection with no JPA/JDBC/repository backing (the genuine
  `*HistoryStore` classes), excluding caches.
- **Security/crypto/authz placeholders** — `TODO: fetch real key…`,
  `derives a local key`, `TODO(role-check)`, controller `Placeholder:` bodies →
  category **S** (crypto = blocker).

## Gate behaviour (baseline-ratchet)

`scripts/guard/check-product-truth.sh` now reports the **true** gap count and
fails on **regression** above `reports/product/product-truth-baseline.json`
(`gapBaseline`, `blockerBaseline`). The baseline is a debt ledger to be **ratcheted
down** as Waves 3–4 land fixes — never raised to absorb new debt. Any new
blocker-severity gap fails the gate immediately.

## Known blind spots (tracked, not hidden)

See `reports/product/product-truth-baseline.json` → `knownBlindSpots`:
HR roster/shift fixtures (no transactional field → not yet matched),
unmounted components (not attributed to a surface), and the fundamental limit
that `REAL_CODE_NOT_PROBED ≠ proven` until a probe artifact exists.
