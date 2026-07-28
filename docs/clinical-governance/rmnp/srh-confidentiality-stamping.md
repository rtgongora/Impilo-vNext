# SRH confidentiality stamping — what is built, what is inert, and exactly what makes it live

**Status:** built, tested, mutation-proven, and **deliberately not in force**.
**Audience:** whoever holds the governance decision to switch this on, and whoever maintains it after.
**Lane:** RMNP clinical domain pack (pct V437, CKP V041).

---

## 1. What this is

tshepo-authz's confidentiality seam went live on 2026-07-26: the PDP decides **which confidential
categories a requester may receive** and carries that on the `VisibilityProfile` obligation.
`SpeciallyProtectedVisibilityGuard` in `shared-core` consumes it. Its javadoc states the division of
labour precisely:

> The PDP decides *which confidential categories this requester may receive*… A system of record
> decides *which of its records* carry the class and in *which category*. Both halves are needed: the
> PDP sees a URL, never a record, so it cannot know that row 7 of a collection is a safeguarding note;
> the service knows that but must not invent its own access rule.

Until this change the second half did not exist anywhere, and the guard had **zero production
callers**. This is that half, for the reproductive records.

## 2. The load-bearing decision: stamp the category, gate the class

Every stamped record carries four values, not one:

| Column | Meaning |
|---|---|
| `sensitivity_class` | The `DataSensitivityClass` — the guard's first argument |
| `confidentiality_category` | The governed category a grant is matched against — the guard's second |
| `confidentiality_basis` | *Why* it was stamped, so the decision stays explicable |
| `confidentiality_policy_version` | *Which* policy version decided it, so a past stamp stays re-readable |

A **category** is a content classification ("this record is SRH content"). It asserts nothing about
protection, so it is written truthfully **today**.

A **class** is a protection claim. It is held at `FULL_CLINICAL` behind
`pct.confidentiality.stamp-class` (default `false`). Stamping `SPECIALLY_PROTECTED` now would make
the record **invisible to the midwife who just wrote it** — the PDP runs in SHADOW and grants no
categories, while the guard fails closed by design. It would also break the rule in
[`adolescent-confidentiality-seed-policy.md`](../adolescent-confidentiality-seed-policy.md) that no
record may carry a protection label that does not protect it.

**Consequence:** a category on a `FULL_CLINICAL` row is the *expected* state, not a defect. There is
deliberately **no** constraint of the form "a category implies the protected class" — that would
forbid the state the system currently runs in.

## 3. The decision table

`ALWAYS` is age-independent. `ADOLESCENT` means the subject was below the governed age **on the act
date**, or holds a `HAS_CAPACITY` determination for that category on or before it.

| Record | Category | Class trigger | Age date |
|---|---|---|---|
| `pct_top_authorisations`, `pct_top_procedures` | `SEXUAL_REPRODUCTIVE_HEALTH` | **ALWAYS** | — |
| loss, `loss_type = TERMINATION` | `SEXUAL_REPRODUCTIVE_HEALTH` | **ALWAYS** | — |
| loss, other types | `SEXUAL_REPRODUCTIVE_HEALTH` | ADOLESCENT | `occurred_on` |
| contraceptive episode | `SEXUAL_REPRODUCTIVE_HEALTH` | ADOLESCENT | `started_on` |
| postnatal contact **with** contraception content | `SEXUAL_REPRODUCTIVE_HEALTH` | ADOLESCENT | `contacted_at` |
| postnatal contact otherwise | *(unstamped)* | never | — |
| pregnancy episode | `SEXUAL_REPRODUCTIVE_HEALTH` | ADOLESCENT | `pregnancy_start_date` |

Three judgements inside that table are worth stating plainly:

- **A termination is confidential at 32 as much as at 15.** The guardian question is not what makes
  it sensitive; the aggregate-only, no-record-level-emit ruling in V435 is.
- **A pregnancy episode does not inherit its outcome's class.** Protecting the whole episode because
  it ended in termination would hide the antenatal care from the maternity team. The TOP rows carry
  the confidence — the same one-directional principle as the PMTCT seam in V111.
- **A routine PNC visit is deliberately unstamped.** Over-marking would put half the postnatal
  register behind a grant nobody holds, and a control that fires everywhere teaches people to route
  around it.

**Age is taken at the act, not today.** A woman who was 17 when a loss occurred and is 19 now stays
protected: the record was created under a promise of confidentiality, and that promise does not lapse
on a birthday.

**Capacity adds eligibility and never removes it.** A `LACKS_CAPACITY` determination cannot strip an
under-age person of the protection the age rule already gave her.

## 4. The two asymmetries

**Stamping fails OPEN; reading fails CLOSED.** An unresolvable date of birth or an unreachable CKP
still records the category, leaves the class at `FULL_CLINICAL`, and records the reason
(`AGE_UNRESOLVED` / `POLICY_UNAVAILABLE`). Never a rejected write.

A false-negative stamp leaves a record readable by the care team. A false-positive makes it invisible
to them — and the read guard already fails closed. Compounding a second closed failure at stamp time
would produce exactly the over-restriction the guard's own documentation calls lethal: *"a teenager
arriving unconscious whose HIV status or medication explains the presentation must not be invisible
to the clinician treating them."*

**The read guard never throws; the write guard does.** `ConfidentialRecordGuard` returns an absent row
or `Optional.empty()`, because a 403 distinguishable from a 404 tells the guardian that the
confidential record *is* there — most of what confidentiality was protecting. `ClinicalAccessGuard`
throws 403 because it rejects **writes**, and refusing a write reveals nothing about existence. Do not
"tidy up" the inconsistency.

## 5. The age is a governed parameter, never a constant

`ConfidentialCarePolicy` deliberately ships **no `engineeringSeed()` factory**, unlike its sibling
`LossThresholdPolicy`. No governed parameter ⇒ no age ⇒ no age-based stamp. A test asserts no age
literal appears in the stamper at all.

The value lives in CKP `clinical.national_policy_parameters` as
`SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS = 18`, `ENGINEERING_SEED` / `UNVERIFIED`. A CHECK
constraint (`chk_npp_ratified_is_verified`) makes it **structurally un-ratifiable** until someone
verifies it. Moving 18 out of a JSON `null` does not make it correct — it makes the guess visible,
dated, attributable and hard to promote by accident.

## 6. Shadow observability — read this before flipping

`ConfidentialRecordGuard` logs `pct.confidentiality.shadow_withhold` with a count, actor and purpose
whenever rows carry a category the requester holds no grant for. **Before the flip this is the only
visible effect of the entire seam**, and it is the measurement the flip decision should rest on: it
shows, in advance, how many reads would start returning fewer rows and to whom.

A flip performed without first watching this signal is a guess about clinical impact.

## 7. The flip list — in order

**Reversing steps 5 and 6 blinds clinicians.**

| # | Flip | Where |
|---|---|---|
| 1 | Set the SRH age, `approvalStatus: RATIFIED`, `effective: true` | `tshepo-authz/src/main/resources/policy/adolescent-confidentiality-pack.json` |
| 2 | `CATEGORY_MAP_RATIFIED` → `true` | `zibo-service/.../ConfidentialCategoryService.java` |
| 3 | Parameter row → `RATIFIED` / `VERIFIED` (new CKP migration, V042–V050) | `clinical.national_policy_parameters` |
| 4 | RMNP read routes live under `/v1/confidential/...` | future controllers — **enforced by `check-confidential-lane-routing.sh`** |
| 5 | `UPDATE policy_rule SET active = true` for V048 rows **and** `confidentialityMode` → `ENFORCE` | tshepo-authz V056, `AuthzProperties.java` |
| 6 | `pct.confidentiality.stamp-class` → `true`, plus a pct V438 backfilling `sensitivity_class` | `PctProperties`, new migration |

### Blocking issues that must be resolved *before* step 1

1. **Mandatory reporting versus confidentiality.** The adolescent pack calls this "the sharpest
   conflict in the whole pack and must be resolved before any threshold is set". A confidential SRH
   record for a minor whose care discloses a sexual offence may be legally required to reach someone
   the stamp hides it from. **Nothing in this implementation resolves it.**
2. **One SRH age or three?** Contraception, STI treatment and termination may each carry a different
   threshold. This ships one parameter. Three would need no schema change but would re-split the
   decision table.

## 8. Known, bounded gaps — recorded rather than silently guarded

- **`PregnancyEpisodeService.pregnant()` is unguarded.** It answers a boolean the record itself would
  refuse. It is called by `FormResolverService` with no request context, so the fail-closed guard
  would blank it and break clinical decision support outright. A real leak, deliberately left, and it
  needs an owner.
- **PNC has no mental-health or GBV vocabulary.** `mood_screen` is an unconstrained `VARCHAR(48)` and
  there is no safeguarding field, so a postnatal depression screen is `MENTAL_HEALTH` content this
  design does not stamp. Raised as a schema gap; inventing the vocabulary in this lane would be
  guesswork.
- **TOP is not yet protected in practice.** Termination records are age-independently confidential
  but stay `FULL_CLINICAL` until the flip, for the reason in §2. A defensible alternative is to stand
  the `/confidential/` PDP lane up for TOP first and stamp it ahead of the rest. **That is a policy
  call, and the seed should not answer it by default.**

## 9. The trap that governs every future route

`ResourceSensitivityClassifier` classifies the confidential lane from a **path substring**
(`confidential`, `safeguarding`, `protected-disclosure`), and all eight V048 rules are pinned to
`path_contains: "/confidential/"`. A reproductive controller mounted at `/v1/pregnancy/...` would,
after the flip, receive **no** `confidentialCategories` from the PDP — and the fail-closed guard would
withhold **every stamped record from every requester, including its author**. The service stays
green, the tests pass, and the ward silently stops seeing its own records.

Each half is correct alone; it is only wrong in combination, and only after a flip in a different
layer. `scripts/guard/check-confidential-lane-routing.sh` makes it a build failure instead, and landed
**before** the first controller exists.
