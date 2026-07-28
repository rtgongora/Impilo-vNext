# RMNP lane — handover, 2026-07-28

Companion to `2026-07-28-coordinator-handover.md`. Everything below is **landed on canonical and
deployed**; nothing is unpushed. This document exists so the successor does not have to re-derive it.

## State: complete and live

RMNP W0–W10 are done. W11 (first `/confidential/` controller + deploy) is done.

**Deployed 2026-07-28, by digest via `kubectl set image` — NEVER helm** (eight services run digests
the committed values do not name; a `helm upgrade` reverts three lanes and reports success):

| Service | Digest |
|---|---|
| pct-service | `sha256:364c104c80ee19448a4082a91a4b1ca380fb3faf5807b73034d9318f098a2775` |
| clinical-knowledge-platform-service | `sha256:fa4e56e22650ecf5a0078303ad14a843201a7a1848f3ada4466118f4644f5592` |
| forms-service | `sha256:f20041d6620233ed7d95e23846e7f6bc3a37071fad3e2ad37b12e6b4cf3cbbe7` |

Live-proven: pct **V437** and CKP **V041** both `success=true` on the target; six tables 0 rows;
`/v1/confidential/reproductive` answers 200; pct→CKP proven end to end with pct's own client
credentials. Next free migration numbers: **pct V438, CKP V042.**

## The one thing that will bite whoever adds the next endpoint

**tshepo-authz classifies confidentiality from the REQUEST PATH.**
`ResourceSensitivityClassifier` matches `confidential` / `safeguarding` / `protected-disclosure`, and
every V048 rule is pinned to `path_contains: "/confidential/"`. A reproductive route mounted anywhere
else gets **no** `confidentialCategories`, and `SpeciallyProtectedVisibilityGuard` fails closed — so
after the governance flip it withholds **every stamped record from every requester, including its
author**, while the service stays green and the tests pass.

`scripts/guard/check-confidential-lane-routing.sh` makes that a build failure. Do not weaken it, and
do not "fix" a failure by removing the stamp.

## Deliberately inert — do not flip without the governance act

`confidentialityMode=SHADOW`, V048 rules `active=false`, `pct.confidentiality.stamp-class=false`.
The ordered six-step flip list is in
[`../clinical-governance/rmnp/srh-confidentiality-stamping.md`](../clinical-governance/rmnp/srh-confidentiality-stamping.md) §7.
**Reversing steps 5 and 6 blinds clinicians.**

**Two blockers sit before step 1 and neither is engineering:**
1. Mandatory reporting vs confidentiality — the adolescent pack calls it "the sharpest conflict in the
   whole pack and must be resolved before any threshold is set". A confidential SRH record for a minor
   whose care discloses a sexual offence may be legally required to reach someone the stamp hides it
   from.
2. One SRH age or three? Contraception, STI treatment and termination may each carry a different
   threshold. One parameter shipped; three needs no schema change but re-splits the decision table.

## Design decisions a successor will be tempted to undo

- **Stamp the category, gate the class.** A category is content classification and ships truthfully
  now; a class is a protection claim and would make records invisible under SHADOW.
- **Stamping fails OPEN; reading fails CLOSED.** A false-negative stamp leaves a record readable by
  the care team; a false-positive makes it invisible, and the read guard already fails closed.
- **`ConfidentialRecordGuard` never throws** — a withheld record must read as absent, because a 403
  distinguishable from a 404 tells a guardian the record is there. This is deliberately the opposite
  of `ClinicalAccessGuard`, which throws on **writes**, where refusing reveals nothing. Do not "tidy
  up" the inconsistency; both javadocs say so.
- **No `engineeringSeed()` on `ConfidentialCarePolicy`**, unlike its sibling `LossThresholdPolicy`. No
  governed parameter ⇒ no age ⇒ no age-based stamp. A test asserts no age literal exists in the
  stamper.
- **`danger_signs_present` passes through including NULL.** NULL means *not screened*; the schema
  (`V436 chk_pnc_screening_gate`) refuses to let it be non-null without a completed screen. Coercing it
  in a read path or a UI re-manufactures the false all-clear.
- **Near-miss carries no confidentiality stamp.** Identification is ordinary clinical classification;
  only the MPDSR *review* is a governed confidential instrument.

## Outstanding, and who owns it

| Item | Owner |
|---|---|
| MPDSR review workflow (trigger already emits; review exists nowhere) | Rito — spec at `../clinical-governance/rmnp/mpdsr-review-contract.md`, **firewall is the load-bearing part** |
| Surgery's stale lease still claims blocked-on-confidentiality | **PO — still open, needs routing** |
| Citizen SMBP + CHW postnatal BFF endpoints | RMNP — contracts in `docs/clinical/rmnp/*-mobile-contract.md`, both must mount under `/confidential/` |
| `pregnant()` unguarded boolean, PNC mental-health/GBV vocabulary | recorded gaps, need owners |

## Working method that earned its place

- **Isolated worktree** (`/opt/impilo/repos/impilo-rmnp-w10`), merging per wave. Protects against the
  shell-deletion hazard *and* makes the directory-pathspec footgun structurally unreachable:
  **worktree > exact-file pathspec > directory pathspec > bare commit.**
- **Mutation-prove every CHECK on live Postgres in a rolled-back transaction, always exercising the
  NULL case** — a CHECK rejects only FALSE. Better still, test the load-bearing one against a **real**
  write: the un-ratifiable guarantee was proven by an actual `UPDATE ... SET approval_status='RATIFIED'`
  on the live database, not a probe that was going to roll back anyway.
- **Verify the jar contains the change before imaging.** The image build does not compile.
- **Resolve the digest from `docker push` output**; the manifest `Accept` header returns empty on this
  registry (it stores an OCI **index**). Sanity-check non-empty before `set image` — that check caught
  a real failure twice in one day.
- **"0 error lines" also means "0 calls attempted."** Prove a link positively.
