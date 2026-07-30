# RMNP lane — handover, 2026-07-28 (revised 2026-07-30, W12)

Companion to `2026-07-28-coordinator-handover.md`. Everything below is **landed on canonical**; nothing
is unpushed. This document exists so the successor does not have to re-derive it.

## What the 2026-07-28 revision of this document got wrong

Recorded because the pattern matters more than the three facts. The W10 handover and the stamping doc
declared work complete that the code did not do, and both read as settled, so the next session began by
trusting them. Three claims failed on inspection:

1. **Two of the six decision-table rows were unimplemented.** The stamping doc's §3 named contraceptive
   and pregnancy episodes as stamped; neither service referenced the stamper or the read guard, and
   `PregnancyEpisodeService.open()` hardcoded `FULL_CLINICAL`. Closed in W12-A.
2. **A second fail-closed trap existed that no guard watched** — the BFF forwards no obligation to pct,
   so after flip step 6 every BFF-mediated read of a stamped record would be withheld from everyone
   including its author. Addressed shadow-first in W12-B; see the stamping doc §9.
3. **Near-miss and RMC were engine-only.** No controller, BFF, indicator or submission path existed for
   either. Built in W12-D and W12-E.

**A finished wave is not a verified one.** State what a reader can check, and if a document runs ahead
of the code, say so in the line that does it.

## State

RMNP W0–W11 are done. **W12 is done**: the two unimplemented stamp rows, the obligation-propagation
capability and its two guards, the pct confidential read and intake routes, both owed BFF surfaces,
near-miss end to end, and the RMC instrument. Not yet re-imaged or re-deployed — W12 is on canonical
but the digests below are still the W11 build.

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
do not "fix" a failure by removing the stamp. Since W12-B it also covers `services/experience-bff/**`,
resolving per **method** rather than per class: a controller is flagged only if it calls a client method
that itself builds a `/confidential/` URL, so the eleven controllers that merely inject a mixed client
stay quiet. If you add a client method reaching a confidential upstream path, every controller calling
it must be mounted under `/internal/v1/confidential/`.

**And the obligation, not just the path.** `scripts/guard/check-visibility-obligation-propagation.sh`
(W12-B) fails the build if the BFF's forwarded header set stops covering every header the visibility
parser reads. It is the guard for the trap in the stamping doc §9, which is the one that will bite
harder than the path trap because it needs no new endpoint to trigger — flip step 6 alone is enough.

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
- **`experience.trust.propagate-obligations` ships false, and the order matters.** The deployed Envoy
  strips the visibility headers on `/internal/v1/public/` only, so today the BFF's refusal to forward is
  the only thing between a forged `x-confidential-categories` and pct's guard. Flipping the flag before
  the edge strips them trades a fail-closed bug for a forge-your-own-grant bug. Strip first.
- **Indeterminate near-miss cases stay in the denominator**, and the indicators report a lower and an
  upper bound rather than one number. Dropping them would make the ratio improve as record-keeping
  degrades, which is exactly backwards for an indicator meant to detect degradation.
- **CKP normalises RMC scores, not the BFF.** Reverse-scoring is a property of the instrument, and the
  instrument is ratifiable content CKP owns. Normalising in the BFF would mean a content correction
  needs a BFF release, and two callers could disagree about polarity while both look right.
- **The RMC narrative and the rating are deliberately not linked.** Rito's anonymous public-case lane
  returns a claim code and no internal id, so an anonymous account cannot be joined to an attributed
  score. That is the privacy property, not a missing foreign key. The narrative is filed **first** so a
  scoring or rating failure never loses what she wrote.
- **Physical abuse and detention route to patient safety, not the facility complaints queue.** An
  anonymous report of abuse sent to the accused facility is worse than no report. `physical_abuse_free`
  is positively worded and therefore **not** reverse-scored — the W10 JSON had that flag inverted, which
  would have made the safest facilities score as the most abusive.

## Outstanding, and who owns it

| Item | Owner |
|---|---|
| MPDSR review workflow (trigger already emits; review exists nowhere) | Rito — spec at `../clinical-governance/rmnp/mpdsr-review-contract.md`, **firewall is the load-bearing part** |
| Surgery's stale lease still claims blocked-on-confidentiality | **PO — still open, needs routing** |
| Strip the visibility headers at the edge, then decide when to flip `propagate-obligations` | **cross-lane security + PO — blocks flip step 6 in practice** |
| Deployed `deploy/helm/.../envoy.yaml` is a third, ungated copy of the Envoy config | **coordinator — the ENVOY-GATE comment names only two files** |
| Next tshepo-authz migration number: V056 is consumed by the break-glass lane and RMNP's V048–V052 band is full | **coordinator — flip step 5 cannot name a number until this is allocated** |
| Re-image and re-deploy pct, CKP and experience-bff for W12 | RMNP — digests above are the W11 build |
| Form 21 renders through the generic DAK renderer, but nothing calls `/classify-form` after submit, so the classification is reachable by API and not yet by a click | RMNP / experience |
| No RMC feedback screen — the BFF and CKP lanes exist and are tested; the citizen-facing form does not | RMNP / experience |
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
