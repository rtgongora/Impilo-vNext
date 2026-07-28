# Nurses Council BRD — source reconciliation pending

**This document records documentation-provenance work that genuinely remains outstanding. It does not
describe any implementation as blocked.**

Implementation proceeds under
[`PRODUCT_OWNER_IMPLEMENTATION_BRIEF.md`](./PRODUCT_OWNER_IMPLEMENTATION_BRIEF.md), which the Product
Owner produced after reading the BRD in full and has approved as the current implementation authority.

## Why this exists

The original BRD source pack — `Nurses_Council_BRD.docx`, its Markdown transcription,
`requirements-inventory.csv` (127 addressable items, `FR-001`…`FR-021` preserved),
`traceability-matrix.csv` and `source-manifest.json` — has not yet been copied onto the repository
host. It exists outside the repository environment.

Verified 2026-07-27: `docs/requirements/regulators/nurses-council/` contained no BRD artefacts, and the
source pack was not reachable from the repository host.

## What remains outstanding

| # | Outstanding item | Depends on |
|---|---|---|
| 1 | **Original-file hash verification** — verify `Nurses_Council_BRD.docx` against the SHA-256 in `source-manifest.json` | BRD pack present in repo |
| 2 | **Exact source-page and line citations** — replace interim `PO-NCZ-*` references with citations to the original document | BRD pack present in repo |
| 3 | **Transcription confirmation** — confirm `Nurses_Council_BRD.md` faithfully matches the original DOCX | BRD pack present in repo |
| 4 | **Final BRD-to-implementation traceability reconciliation** — map the interim identifiers to the original business processes, `FR-001`…`FR-021`, the eight reports and the four registers | Items 1–3 |

## What is *not* outstanding

- Implementation authority — the Product Owner has confirmed it.
- The functional and architectural specification — recorded in the approved brief.
- Interim traceability — maintained now in `traceability-matrix.csv` against the `PO-NCZ-*` and
  `PLATFORM-*` identifiers.
- Any wave of the delivery plan. **Nothing in the delivery plan is gated on this document.**

## Reconciliation rules when the BRD lands

1. Preserve the original wording. Do not silently rewrite, complete or reinterpret a source requirement.
2. Preserve the existing `FR-001` … `FR-021` identifiers, and use the generated identifiers for other
   source elements (`BP-*`, `BR-*`, `REP-*`, `REG-*`, `NFR-*`, `SCOPE-IN-*`, `SCOPE-OUT-*`).
3. Map each interim `PO-NCZ-*` identifier onto the source items it covers; where an interim requirement
   turns out to span several source items, split the traceability row rather than widening the source
   reference.
4. Where implementation added functionality required by the broader Impilo regulatory architecture but
   not stated in the BRD, keep it classified `PLATFORM_DERIVED`. **Never present a platform-derived
   requirement as a Nurses Council BRD requirement.**
5. Where a source item turns out to need a council policy decision, reference the source id, add or
   update an entry in [`COUNCIL_DECISIONS_REQUIRED.md`](./COUNCIL_DECISIONS_REQUIRED.md), keep the
   effective-dated configuration seam, and **do not invent the missing policy**. Distinguish
   implementation readiness from policy activation readiness.
6. Reconciliation may refine traceability. **It should not require rebuilding sound functionality** —
   if it appears to, that is a signal the brief and the BRD diverged, and the divergence should be
   raised rather than silently resolved in code.

## How to clear this document

Copy the source pack to `docs/requirements/regulators/nurses-council/`, then work items 1–4 in order.
When all four are complete, replace this file with a short note recording the verification date, the
verified hash, and the commit in which reconciliation landed.
