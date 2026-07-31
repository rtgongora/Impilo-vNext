# MPDSR — maternal and perinatal death surveillance and response

**Status:** **built in Rito** (`rito-quality-safety-service`, W14-E, 2026-07-31). PO confirmed SoR 2026-07-31.
**Audience:** QI/clinical governance operators — committee review workflow in `/rito/mpdsr`.
**Companion:** [`srh-confidentiality-stamping.md`](srh-confidentiality-stamping.md) ·
near-miss identification is RMNP's (`rmnp-maternal-near-miss.json`) and is already built.

---

## 1. What already works, and what does not

**The trigger is real and wired.** `PublicHealthScreener` (pct) computes `deathReviewRequired` and
emits `DEATH_REVIEW_REQUIRED` from `DeathWorkflow`, flagging `MATERNAL`, `PERINATAL`, `NEONATAL` and
`STILLBIRTH` deaths. `death_review_required` is a column on the death case (pct `V022`).

**Near-miss identification is built** (RMNP W10-B): WHO organ-dysfunction criteria over the shared
`ClassificationEngine`, deliberately carrying no confidentiality stamp because it is ordinary clinical
classification.

**The review is built in Rito (W14-E).** `MPDSR_REVIEW` case type, idempotent intake from
`DEATH_REVIEW_REQUIRED` (Kafka + `POST /internal/v1/mpdsr/reviews/from-death-event`), committee/
findings/actions lifecycle, and firewalled facility-readiness tasks with contaminated-payload tests.
UI: `/rito/mpdsr` (ops/QI — not citizen, not EHR maternity chart).

## 2. Why RMNP is not building it

Three reasons, in increasing order of importance.

1. **Ownership is unconfirmed.** Three separate design documents mark Rito's ownership of mortality
   review as a *provisional assumption pending confirmation*
   (`docs/design/rito/sor-boundary-rito-vs-patient-safety.md`, `rito-service-design.md`,
   `docs/audits/rito/TRUNCATION-GAPS.md` item A5). Building into another lane's service on an
   assumption its own docs flag is how duplicate systems of record get created.
2. **MPDSR needs its own confidentiality treatment, not the general clinical one.** The SRH stamping
   seam is about who may read a patient's record. MPDSR is about protecting a *review conversation* —
   a different subject, a different threat model, and a different set of people to protect it from.
   Applying the clinical seam to it would be a category error.
3. **The firewall below is the hard part**, and it is a design constraint rather than a feature. It
   needs stating clearly before anyone writes code against the trigger.

## 3. The MPDSR firewall — the load-bearing constraint

> **A maternal death review MAY PROMPT a facility readiness assessment. It MUST NOT BE one.**

MPDSR is confidential and no-blame **by design**, because that is the only condition under which
clinicians tell the truth about what happened. Facility readiness, by contrast, feeds referral
routing and public-facing surfaces.

Wire them together and "we had no magnesium sulfate that night" becomes a visible downgrade of the
facility. The next review team learns this. Their answers get shaded — and **a shading review still
looks exactly like a review**, so the degradation is invisible. The result is that the more valuable
instrument is destroyed in order to populate the less valuable one, and nobody can tell.

This is the same shape as the regulation firewall already established at
`provider-reputation-doctrine.md:185`: a rating never mutates a licence; patterns become governed
referrals.

### What the firewall requires, concretely

| Rule | Why |
|---|---|
| A review raises a **task**, routed to whoever owns facility readiness — **never to the review team** | The reviewers must not be the assessors, or the review becomes an audit |
| **No case content crosses.** Not the narrative, not the findings, not a date that identifies the death | A "task" carrying the story is the review by another name |
| The task is **non-attributable to a specific review** | If a readiness assessment can be traced back to one death, the reviewers are exposed |
| The readiness assessment remains a **separate attributable act**, with its own `method` and `assessed_by` | An assessment nobody signed is not an assessment |
| **Never** write a readiness assessment as a side effect of clinical activity | Already an RMNP hard law: one difficult caesarean two years ago would mark a facility capable tonight |

**Enforce this with a contaminated-payload boundary test, not a comment.** A test that constructs a
review with identifying content, raises the task, and asserts the task payload contains none of it —
and which fails if a field is added that carries it through.

## 4. What the review needs to record

Sketched, not prescribed — the owning lane should shape it:

- **Identification** — the death or near-miss, its trigger event, and the WHO classification.
- **Committee and meeting** — who reviewed, when, at what level (facility / district / national).
- **Findings** — modifiable factors across the three delays (deciding to seek care, reaching care,
  receiving care). This is the analytic spine of MPDSR and the reason it exists.
- **Recommendations and actions** — with an owner and a due date, tracked to closure. Rito's existing
  CAPA / QI-plan substrate fits here without modification.
- **Response loop closure** — the "R" in MPDSR. A surveillance system that never closes its
  recommendations is a register, not a response.

## 5. Indicator contract back to RMNP

RMNP owns the indicators and needs only counts, never content:

- **Near-miss-to-death ratio** — near-misses per maternal death.
- **Mortality index** — deaths ÷ (deaths + near-misses). Rising means women reaching severe
  complications are dying more often, which a maternal mortality ratio alone can hide.
- **Review coverage** — reviews completed ÷ deaths requiring review.
- **Response closure** — recommendations closed ÷ raised.

`IndicatorEngine` already enforces the rule these depend on: an indeterminate case stays in the
denominator. **A surveillance denominator never silently loses a case** — dropping the unreviewed
deaths would make coverage look perfect precisely where it is worst.

## 6. The decision this needs

**Who owns the maternal/perinatal death review case type?** Options, with the trade-off:

- **Rito** (the provisional assumption). It already holds the CAPA/QI substrate and a `NEAR_MISS`
  case type, and owns quality and safety. Risk: MPDSR's confidentiality needs are stronger than
  Rito's general case confidentiality, so it would need a distinct treatment inside Rito.
- **A dedicated surveillance surface.** Cleaner confidentiality boundary; costs a new system of
  record for something Rito is 80% shaped for, which the architecture guardrails discourage.

RMNP's view: **Rito, with an MPDSR-specific confidentiality treatment and the firewall above enforced
by test.** But this is not RMNP's call to make, and the three docs asking for confirmation should get
an answer before anyone builds.

## 7. What RMNP will do once it is built

Consume counts for the indicators in §5, and nothing else. RMNP does not read review content, does
not display findings, and does not surface a facility's review history — for the reason in §3.
