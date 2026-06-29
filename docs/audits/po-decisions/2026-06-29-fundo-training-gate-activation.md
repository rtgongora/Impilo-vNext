# PO Decision Required: Fundo training-gate enforcement (block vs warn)

Decision ID: PO-20260629-01
Wave: Phase 1 (SYS-1) — Fundo training-gate (G-FU-02)
Status: OPEN
Blocking: only the training-gate *enforcement activation*; all other remediation continued.
Session Behaviour: Deferred safely; remediation continued.

## Context

`learning-service` exposes `FundoTrainingGateService.evaluate(...)` — a clean signal that answers
"is required-course set R satisfied for subject S?" (COMPLETED enrolment + valid certificate). It is
explicitly **not** the gate (the service doc: "learning-service is not the workforce gate"). No
consumer currently enforces it: vashandi check-in / workspace-entry does not consult training, and
there is no fundo client in vashandi. So "completion unlocks workspace access" (Provider-Experience
Journey C / G-FU-02) is specified but not enforced.

## Why this needs PO input

Two coupled decisions are clinical-operations / product policy, not a safe technical default:

1. **Block vs warn.** Should a licensed, scope-valid provider with **incomplete** required training be
   **denied** workspace entry, or **warned/flagged** and allowed? Denying a licensed provider can
   disrupt care delivery (a clinical-availability risk); warning preserves access but weakens the gate.
2. **Requirement mapping.** Which workspaces/roles require which courses, and the consequences of an
   unknown/unmapped requirement (fail-open vs fail-closed). This mapping does not yet exist.

Both have patient-safety and workforce implications and should be owned by the PO / clinical lead.

## Options

### Option A — Advisory (warn, do not block)
- Description: vashandi check-in queries the fundo training-gate; if incomplete, surface a
  readiness warning + flag, but allow entry. Audited.
- Pros: never blocks a licensed provider; no care-disruption risk; safe to ship.
- Cons: weaker gate; non-completion is visible but not enforced.
- Risk: Low.

### Option B — Hard gate (block when required training incomplete)
- Description: vashandi check-in denies workspace entry when a configured requirement is unmet
  (break-glass / supervisor override available).
- Pros: strongest readiness guarantee.
- Cons: can deny legitimate clinical access if the requirement mapping is wrong/incomplete
  (clinical-availability risk); requires a complete, correct requirement mapping first.
- Risk: Medium–High until the requirement mapping is governed.

### Option C — Per-requirement configurable (enforce flag on each requirement)
- Description: each training requirement carries `enforcement = ADVISORY | BLOCKING`; default ADVISORY.
- Pros: lets the PO escalate specific high-stakes requirements to blocking over time.
- Cons: more model surface; needs the requirement mapping.
- Risk: Low at default; controlled escalation.

## Claude recommendation

**Option C with default ADVISORY** (equivalently Option A until any requirement is explicitly set
BLOCKING). It is non-disruptive by default, lets the PO escalate specific requirements deliberately,
and is the conservative-safe default for a readiness gate (avoid denying legitimate clinical access).

## Safe interim action taken

Applied the conservative default: **kept existing behaviour — did NOT ship a blocking gate.** No
runtime change that could deny a licensed provider. The signal (`FundoTrainingGateService`) remains
available for the consumer to be built once the policy is set.

## What was deferred

- The vashandi → fundo training-gate **consumer/seam** (new fundo client + check-in/workspace-entry
  integration).
- The workspace/role → required-course **requirement mapping**.
- The **block-vs-warn** enforcement policy.

## Impact if no PO response

Training-gate enforcement (G-FU-02) stays at "signal exists, not enforced" — a missing *feature*, not
an unsafe state (no provider is wrongly blocked; no untrained access is newly *enabled* by this
deferral). All other remediation (Phase-1 policy enforcement, in-service bindings, later phases)
continued and is pushed.

## Optional email link

[Email Robert for decision](mailto:rgongora536@gmail.com?subject=Impilo%20vNext%20PO%20Decision%20Required%20-%20PO-20260629-01&body=Please%20review%20docs/audits/po-decisions/2026-06-29-fundo-training-gate-activation.md%20on%20branch%20claude/crazy-merkle-3ad1a1.)
