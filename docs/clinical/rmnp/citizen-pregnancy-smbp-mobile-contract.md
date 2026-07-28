# Citizen pregnancy and home blood pressure — contract for the mobile citizen app

**Audience:** the mobile-recovery lane.
**Status:** **the backend is PART-BUILT. Read §2 before planning UI work** — unlike the
partograph/CTG contract, this one is not "nothing needs new server work". Two of the three pieces
need a BFF surface that does not exist yet, and this document says precisely which.
**Companion:** [`partograph-ctg-mobile-contract.md`](partograph-ctg-mobile-contract.md) is the
provider-app precedent and the format this follows.

---

## 1. Why this contract is honest about gaps

The mobile lane's own design law, adopted by RMNP:

> **No ANC/PNC write path may return 200 over a destination nothing reads.**

Writing a contract that names endpoints which do not exist would break that law in the document
rather than in the code. So §2 separates what is callable today from what must be built, and by whom.

## 2. What exists, and what does not

| Capability | Clinical engine | Record | BFF endpoint | Usable from mobile today |
|---|---|---|---|---|
| Birth destination / referral routing | `BirthDestinationService` | — | **`/internal/v1/maternity/birth-destination`** | **Yes** |
| Maternity summary | — | pct | **`/internal/v1/maternity`** | **Yes** |
| Home BP (SMBP) escalation | `SmbpEscalationService` + `HomeBpSeriesReducer` (CKP) | telemonitoring `tm_readings` | **none** | **No — needs a BFF surface** |
| Pregnancy episode (booking, EDD, gestation) | `GestationalAgeEngine` | pct `V059` | **none** | **No — needs a BFF surface** |

**The two gaps are RMNP's to close**, not the mobile lane's. They are listed in §6 with their
constraints, because the constraints determine the shape of the endpoint and therefore the shape of
the screen.

## 3. Governed form definitions — do not hard-code fields

| Surface | `formKey` | Seed |
|---|---|---|
| Home BP readings | `impilo.smbp.home.readings.v1` | `20-smbp-home-readings.json` |
| ANC contact | see the ANC seeds | `13-anc-contact-followup.json` |

Render from the fetched definition. The `linkId` of every field **is** the engine's fact key, so a
hard-coded field name that drifts from the seed silently stops being evaluated — the rule does not
error, it simply never fires.

## 4. Behaviours the UI must preserve

These are clinically load-bearing. Each one exists because the opposite behaviour has a specific way
of hurting someone.

1. **A severe reading is flagged BEFORE averaging, and scanned across every reading — including the
   discarded first-of-session.** A calm mean must never bury one severe reading. If the UI shows only
   an average, it will hide the reading that mattered.
2. **Too few readings is `INSUFFICIENT_DATA`, never `CONTROLLED`.** A short quiet series is not
   reassurance. Render it as "not enough readings yet", never as a green state.
3. **The sustained-elevated referral is gated on sufficiency**, so it cannot fire from two readings.
4. **`NOT_ASSESSED` is distinct from a negative.** Everywhere in this pack a blank means "not asked",
   never "absent". A UI that renders unanswered as "no" manufactures reassurance.
5. **Never render "no danger signs" from an empty list.** It is only renderable behind an explicit
   `screeningComplete` flag — the postnatal record enforces this in its schema (`V436`
   `chk_pnc_screening_gate`), and the citizen surface must not undo it.
6. **Referral copy carries "call ahead" verbatim, including for a full CEmONC facility.**
   `operational` does not mean open, staffed, or with capacity — there is no live-status signal
   anywhere in the estate.
7. **`INSUFFICIENT_EVIDENCE` is never collapsed into "no".** For EmONC readiness, "nobody has
   assessed this facility" and "this facility cannot" must render differently: the difference is
   phoning ahead versus driving past. Today **every** facility returns `INSUFFICIENT_EVIDENCE` with
   9× `UNKNOWN` signal functions — that is the honest headline, not a bug to hide.

WHO-quality narrative to reuse verbatim where readiness is unknown:

> Nothing is known about its emergency obstetric capability — this is not a statement that it has
> none. Call ahead.

## 5. Payload keys

Requests are **camelCase**, responses are **snake_case**, as elsewhere in this estate. Send
`cervicalDilationCm`, read `cervical_dilation_cm`. Do not build a request record in a test and assume
it matches the wire — deserialise the literal JSON the client sends, because a snake_case request
record against a camelCase client is a **silent 400** that looks identical to a validation failure.

## 6. What RMNP still owes, with the constraint that shapes it

**(a) An SMBP submission and series-verdict surface.** The reducer consumes readings; it must not
re-ingest them. Telemonitoring owns raw home-vital persistence (`tm_readings`) and is the single SHR
writer of monitoring-band Observations, so the BFF endpoint composes
`GET /internal/v1/readings/by-plan/{planId}` and the CKP verdict — it does not create a second
reading store.

**(b) A citizen pregnancy-episode surface.** Constrained by the duplicate-prevention design: an
already-booked pregnancy must return **409 with the existing id and a reconciliation task, never a
500** — a 500 here silently loses an offline booking.

**Both are subject to the confidential-lane rule.** A controller exposing `PregnancyEpisodeEntity`
must be mounted under a path containing `/confidential/`, or after the governance flip the
fail-closed guard will withhold every stamped record from every requester — including its author.
`scripts/guard/check-confidential-lane-routing.sh` fails the build otherwise. See
[`srh-confidentiality-stamping.md`](../../clinical-governance/rmnp/srh-confidentiality-stamping.md) §9.

## 7. Deliberately not here

Provider-facing labour monitoring (that is the partograph/CTG contract), the CHW community-postnatal
surface (its own contract), and anything requiring the confidentiality flip, which is a governance act
and not scheduled.

## 8. Definition of done

A citizen registered from zero can book a pregnancy, submit home BP readings, see an honest series
verdict including `INSUFFICIENT_DATA`, and be routed to a birth destination whose readiness is
described honestly — with no screen rendering an unanswered question as a negative.
