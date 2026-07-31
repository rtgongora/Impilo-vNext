# Citizen pregnancy and home blood pressure — contract for the mobile citizen app

**Audience:** the mobile-recovery lane.
**Status:** **BUILT through W13 (2026-07-31).** The BFF surfaces this document was written to
specify exist; §2 names them and records which channels have LIVE product UI. Nothing here needs new
server work for the rows marked Yes. §4 behaviours remain the UI's to preserve.
**Companion:** [`partograph-ctg-mobile-contract.md`](partograph-ctg-mobile-contract.md) is the
provider-app precedent and the format this follows.

---

## 1. Why this contract is honest about gaps

The mobile lane's own design law, adopted by RMNP:

> **No ANC/PNC write path may return 200 over a destination nothing reads.**

Writing a contract that names endpoints which do not exist would break that law in the document
rather than in the code. So §2 states what is callable today, and it is kept true as things land: when
this document was first written two of its rows read **none**, and the honest gap is what got them
built. Both were closed in W12; if you find a row here that the code does not answer, that is a defect
in this file and not a feature to work around.

## 2. What exists

| Capability | Clinical engine | Record | BFF endpoint | Usable from mobile today |
|---|---|---|---|---|
| Birth destination / referral routing | `BirthDestinationService` | — | **`/internal/v1/maternity/birth-destination`** | **Yes (W13-A)** — citizen pregnancy + provider maternity |
| Maternity summary | — | pct | **`GET /internal/v1/maternity/summary`** | **Yes (W13-A)** — provider / EHR; not a citizen chart |
| Home BP (SMBP) series verdict | `SmbpEscalationService` + `HomeBpSeriesReducer` (CKP) | telemonitoring `tm_readings` | **`GET /internal/v1/confidential/maternity/smbp/verdict?planId=…`** | **Yes (W12)** |
| Pregnancy episode booking | `GestationalAgeEngine` | pct | **`POST /internal/v1/confidential/maternity/pregnancy-episodes`** | **Yes (W12)** |
| Current pregnancy / obstetric history | `GestationalAgeEngine` | pct | **`GET …/pregnancy-episodes/{cpid}/current`**, **`GET …/pregnancy-episodes/{cpid}`** | **Yes (W12)**; clinician CPID UI in **W13-B** |
| Current contraception | pct confidential | pct | **`GET /internal/v1/confidential/reproductive/contraception/{cpid}`** (`"me"` for citizen) | **Yes (W13-B)** — citizen current plan only |
| Contraception history / losses / TOP | pct confidential | pct | **`…/contraception/…/history`**, **`…/losses/{motherCpid}`**, **`…/top-authorisations|terminations/{subjectCpid}`** | **Yes (W13-B)** — **clinician-primary**; do not put TOP/loss on citizen |
| Respectful-care feedback | `RespectfulMaternityCareService` (CKP) | rito | **`GET/POST /internal/v1/maternity/respectful-care/{instrument,feedback}`** | **Yes (W12)** |

**Note the `/confidential/` segment on the SMBP, pregnancy-episode, and reproductive rows.** It is not
decoration and it is not rewritable: see §6.

**Submitting readings is still telemonitoring's**, not this surface's. The verdict endpoint composes
`GET /internal/v1/readings/by-plan/{planId}` and asks CKP what the series means; it deliberately does
not accept a reading, because a second reading store would put her home blood pressure in two places
that disagree.

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

## 6. The W12 surfaces, and the constraints that shaped them

**(a) `GET /internal/v1/confidential/maternity/smbp/verdict?planId=…&dangerSignPresent=…`**

The reducer consumes readings; it does not re-ingest them. Telemonitoring owns raw home-vital
persistence (`tm_readings`) and is the single SHR writer of monitoring-band Observations, so this
endpoint composes `GET /internal/v1/readings/by-plan/{planId}` with the CKP verdict rather than
creating a second reading store. **Readings are still submitted to telemonitoring.**

Three things about it the UI has to respect:

- **`dangerSignPresent` is optional and must stay optional.** Omit it when she was not asked. Sending
  `false` on her behalf asserts she has no danger signs, and on this pathway that is the most dangerous
  default available. The engine reads absent as UNKNOWN.
- **A failure to read the series is a 502, not an empty series.** `READINGS_UNAVAILABLE` and
  `CKP_UNAVAILABLE` each carry a `clinical_note` saying so in words. Do not render either as
  `INSUFFICIENT_DATA` or as a controlled verdict — "keep monitoring" is the wrong advice for "we cannot
  see anything".
- **`meta.raw_reading_count` and `meta.paired_reading_count` both appear, and differ on purpose.**
  Telemonitoring stores systolic and diastolic as two rows sharing a `measuredAt`; only complete pairs
  become blood pressures, and a quarantined half drops the pair rather than being completed from a
  neighbour. A plan with 40 raw rows that pairs into 3 has a device or metric-code fault, and without
  the raw count that surfaces only as an unexplained `INSUFFICIENT_DATA`. Show it in a diagnostic
  affordance, not to her.

One caveat on §4.1: the surface does **not** mark `firstOfSession`, because telemonitoring records no
session boundary, so no reading is discarded from the mean. That is the safe direction — a reading
wrongly counted dilutes the mean slightly, whereas a reading wrongly discarded is one fewer toward
sufficiency and could hold a woman at `INSUFFICIENT_DATA`. The severe check sees every reading either
way, so no severe reading is lost to it. If the client ever learns session boundaries, send them.

**(b) `POST /internal/v1/confidential/maternity/pregnancy-episodes`** plus
`GET …/pregnancy-episodes/{cpid}/current` and `GET …/pregnancy-episodes/{cpid}`.

pct's statuses are forwarded unchanged, and the client must distinguish all four:

| Status | Meaning | What the app does |
|---|---|---|
| **201** | booked | clear the outbox entry |
| **200** | this exact offline packet was already applied (`clientOfflineId` replay) | clear the outbox entry — **not** a duplicate |
| **409** | she already has an open pregnancy; the body carries the existing id | reconcile against that id, keep her draft |
| **422** | undatable — no LMP, no scan, nothing to date from | ask for a date; do not retry unchanged |

**409 is a feature, and a 500 here is a defect** — a 500 silently loses an offline booking, and the
woman who loses it is the one who booked in a place with no signal. The reads return an empty body
rather than a 404, because absence and withholding must read alike.

**Both are on the `/confidential/` lane, and the segment is not rewritable.** tshepo-authz classifies
confidentiality from the **request path**, so a route exposing `PregnancyEpisodeEntity` mounted anywhere
else receives no `confidentialCategories`, and after the governance flip the fail-closed guard withholds
every stamped record from every requester — including its author — while the service stays green and the
tests pass. `scripts/guard/check-confidential-lane-routing.sh` fails the build otherwise. See
[`srh-confidentiality-stamping.md`](../../clinical-governance/rmnp/srh-confidentiality-stamping.md) §10.

**(c) `GET /internal/v1/maternity/respectful-care/instrument` and `POST …/feedback`** — deliberately
**not** on the confidential lane, because it is her account of how she was treated rather than a
clinical record about her, and it is anonymous by default. Fetch the instrument; never hard-code the
prompts or the scale. Four things carry:

- **Anonymous unless she opts out.** `anonymous: false` is the only thing that attaches her identity.
- **Her narrative and her scores are not linked**, and that is the privacy property rather than a
  missing field. The anonymous lane returns a **claim code** and no internal id — surface the claim code,
  it is how she follows up without being named.
- **Reverse-scoring is CKP's**, not yours. Submit raw answers on the scale the instrument gives; do not
  invert anything locally.
- **The narrative is filed first**, so a scoring failure never loses what she wrote. If the response
  reports that scores were not stored, her account still was — say so rather than asking her to retype it.

## 7. Deliberately not here

Provider-facing labour monitoring (that is the partograph/CTG contract), the CHW community-postnatal
surface (its own contract), and anything requiring the confidentiality flip, which is a governance act
and not scheduled.

## 8. Definition of done

A citizen registered from zero can book a pregnancy, submit home BP readings, see an honest series
verdict including `INSUFFICIENT_DATA`, and be routed to a birth destination whose readiness is
described honestly — with no screen rendering an unanswered question as a negative.
