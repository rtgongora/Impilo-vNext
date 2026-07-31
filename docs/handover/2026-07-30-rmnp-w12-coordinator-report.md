# RMNP W12 — report to the coordinator

**From:** RMNP lane, branch `claude/rmnp-w10-completion`, pushed to
`claude/staging-ux-orchestration-remediation-Yypyl`.
**Date:** 2026-07-30.
**Why this file exists:** four of these items are not RMNP's to decide, and `docs/registry/**` is
report-only for this lane. Nothing below has been actioned in the registry — each one needs a
coordinator act.

---

## 1. tshepo-authz: flip step 5 has no number, and RMNP's band is full

**What we found.** The W10 flip list named **V056** for activating the V048 confidentiality rules.
V056 is now `V056__break_glass_governance_policy_rules.sql`, taken by the break-glass lane. The obvious
repair — take V057 — is also wrong: RMNP's registered tshepo-authz band is **V048–V052**
([`../registry/iatg-rmnp-leases.md`](../registry/iatg-rmnp-leases.md) §2), **every number in it is
consumed**, V053–V056 belong to other lanes, and V057 sits outside any band this lane holds.

**What we did.** Nothing. The stamping doc's step 5 now says the number is unanswerable and names this
report. Taking V057 unilaterally is precisely how two sessions cut the same number, which is the failure
`check-migration-version-collisions.sh` exists to catch, and we would rather block a flip that is
already blocked on two governance questions than win a race.

**What we need:** a number, or an extension of RMNP's authz band. Note that `services/tshepo-service/**`
is NO-TOUCH for this lane in any case, so the migration itself is not ours to write.

## 2. The visibility headers are forgeable at the edge — cross-lane security

**What we found.** `request_headers_to_remove` strips `x-obligations` on `/internal/v1/public/` only.
On every other route a client-supplied `x-obligations` or flat visibility header survives the edge, and
the ext_authz response overwrites it only when the PDP emits one. The deployed config renders ext_authz
**off**.

**Why it is not academic.** Today nothing downstream of the BFF reads those headers, because the BFF does
not forward them — see item 3. That accident is the only thing standing between a forged
`x-confidential-categories` and pct's fail-closed guard. The moment anyone forwards obligations for any
reason, forging a grant becomes possible estate-wide, not just for RMNP's records.

**What we need:** the visibility headers added to a **global** `request_headers_to_remove`, by whoever
owns the trust edge. This gates RMNP's flip step 4b, and we have not flipped anything that depends on it.

## 3. The BFF does not propagate the visibility obligation — estate-wide, latent

**What we found.** `ServiceClientConfig` forwards roughly forty trust headers and forwards neither
`x-obligations` nor any flat visibility header, while the BFF calls services directly
(`PCT_BASE_URL: http://pct-service:8088`), so ext_authz never re-runs. Every guard that consumes
`VisibilityContextHolder` therefore sees a null profile on every BFF-mediated call.
`SpeciallyProtectedVisibilityGuard` fails closed on null by explicit design.

**Consequence for any lane that stamps records, not only RMNP.** After a confidentiality flip, every
BFF-mediated read of a stamped record is withheld from everyone including its author, while the direct
Envoy route keeps working. `X-Purpose-Of-Use` *is* forwarded, so emergency reads survive and routine care
does not — the failure presents as "confidentiality works in an emergency and breaks in clinic", which
reads like a policy quirk rather than a plumbing bug, and would be debugged as one.

**What we did.** Shadow first, and deliberately not fixed: a shadow signal
(`experience.visibility.shadow_restrict`) that measures which axis would restrict per outbound call; the
forwarding capability behind `experience.trust.propagate-obligations`, **default false**; and
`scripts/guard/check-visibility-obligation-propagation.sh`, which fails the build if the forwarded set
stops covering every header the parser reads. `AggregateVisibilityGuard` and `ClinicalVisibilityGuard`
are **permissive** on a null profile and **restrictive** on a present one, so flipping the flag tightens
reads across the estate in one act. That is a governance decision with a measurable size, and the shadow
signal exists so the size is known before the act.

**What we need:** item 2 resolved first, then a decision on when to flip, informed by the shadow counts.

## 4. The deployed Envoy config is a third, ungated copy

`deploy/helm/impilo-vnext/templates/envoy.yaml` is a third copy of the Envoy configuration. The
`ENVOY-GATE: both files change together` comment names only two. A header-strip rule added to the two
named files — which is exactly the fix item 2 asks for — would not reach the deployed one, and the gate
would report success.

**What we need:** the gate extended to all three copies, or the third one made a render of the same
source. We have not edited it; a header change there is item 2's, not RMNP's.

---

## What RMNP did land in W12, for context

Detail in [`2026-07-28-rmnp-lane-handover.md`](2026-07-28-rmnp-lane-handover.md).

- **W12-A** — the two decision-table rows the governance doc claimed and the code did not implement
  (contraceptive and pregnancy episodes: stamped, and their disclosure reads guarded), `coverage()`
  filtering before computing, and two audit defects: a stale stamp surviving re-record, and a javadoc
  claiming stamping was deferred while the code already stamped. **No pct migration** — `V437` had
  already added the columns.
- **W12-B** — items 3 above, shadow-first, plus `check-confidential-lane-routing.sh` extended to
  `services/experience-bff/**` resolving per method rather than per class.
- **W12-C** — pct guarded confidential reads for contraception, pregnancy episodes and TOP
  authorisations; the two owed BFF surfaces (citizen SMBP verdict and pregnancy booking, CHW community
  postnatal), both under `/internal/v1/confidential/`.
- **W12-D** — near-miss end to end: CKP controller, BFF proxy including a form-21 translation, and the
  near-miss-to-death ratio and mortality index with indeterminate cases held in the denominator and the
  result reported as bounds.
- **W12-E** — the RMC instrument on Rito's existing rating and anonymous public-case lanes, with the real
  measure codes. Fixed a polarity defect in the W10 measure set: `physical_abuse_free` was flagged
  reverse-scored though positively worded, which would have scored the safest facilities as the most
  abusive.

**No migrations in any of it**, so no collision risk and no `RENAME COLUMN` image-coupling hazard.
Consumed nothing from any band. **Not yet re-imaged or re-deployed** — pct, CKP and experience-bff all
carry W12 changes and the deployed digests are still the W11 build.
