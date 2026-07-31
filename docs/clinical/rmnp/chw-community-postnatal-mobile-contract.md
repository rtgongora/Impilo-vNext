# CHW community postnatal care — contract for the mobile provider app (Outreach mode)

**Audience:** the mobile-recovery lane. Belongs in provider-app's existing **Outreach** mode, not a
new workspace.
**Status:** **BUILT through W13 (2026-07-31)** — CHW community postnatal (W12) plus facility PNC /
newborn journeys and contraception BFF reads (W13). §2 names the endpoints. §4 is still the UI's work.
**Companion:** [`partograph-ctg-mobile-contract.md`](partograph-ctg-mobile-contract.md) (format
precedent) · [`citizen-pregnancy-smbp-mobile-contract.md`](citizen-pregnancy-smbp-mobile-contract.md).

---

## 1. What this is for

A postnatal contact that happens at **home or in the community**, by a CHW, often offline, often for a
woman who will not return to a facility. The WHO PNC schedule is within 24 hours, day 3, day 7–14 and
week 6 — and the contacts most likely to be missed are exactly the ones a CHW makes.

Until pct `V436` there was nowhere to record such a visit at all. There is now.

## 2. What exists, and what does not

| Capability | Engine / record | BFF endpoint | Usable today |
|---|---|---|---|
| Record a postnatal contact (home / community / virtual first-class) | pct `V436` + `PostnatalContactService` | **`POST /internal/v1/confidential/community/postnatal/contacts`** | **Yes (W12)** — Outreach Postnatal |
| Read a mother's postnatal contacts | as above | **`GET /internal/v1/confidential/community/postnatal/contacts/{motherCpid}`** | **Yes (W12)** |
| Facility PNC maternal (form 16) | forms seed + encounter submit | forced `impilo.pnc.maternal.contact.v1` + **`POST …/clinical/postnatal/maternal/assess`** | **Yes (W13-C)** — distinct from CHW boolean screen |
| Facility PNC newborn (form 17) | forms seed; PSBI stays on young-infant | forced `impilo.pnc.newborn.contact.v1` | **Yes (W13-C)** — do not restate PSBI |
| PNC maternal danger signs | `rmnp-pnc-maternal-danger-signs.json` (CKP) | BFF postnatal assess | **Yes (W13-C)** — gate on `screeningComplete` |
| PNC newborn danger signs (delegates PSBI) | `rmnp-pnc-newborn-danger-signs.json` | BFF postnatal assess + young-infant path | **Yes (W13-C)** |
| Postpartum family planning | contraceptive episode (`V430`) | **`GET /internal/v1/confidential/reproductive/contraception/{cpid}`** | **Yes (W13-B)** — BFF proxy on confidential lane |

§6 states why the path segment is `/confidential/` and why it is not rewritable.

## 3. Governed form definitions

| Surface | Seed |
|---|---|
| PNC maternal contact | `16-pnc-maternal-contact.json` |
| PNC newborn contact | `17-pnc-newborn-contact.json` |

Render from the fetched definition; `linkId` **is** the fact key.

**The newborn form deliberately does not restate PSBI.** Systemic serious-bacterial-infection signs
live in the young-infant assessment (form 12) and are evaluated by the single platform definition.
Three definitions of a septic newborn is how a mother is told her baby is fine on one screen and
referred on another. Do not "helpfully" merge them in the UI.

## 4. Behaviours the UI must preserve

1. **"No danger signs" is only renderable behind an explicit `screeningComplete`.** This is enforced
   in the schema — `V436 chk_pnc_screening_gate` makes `danger_signs_present` non-null *only* when the
   screen was completed. An unscreened contact must render as **"not screened"**, never as a
   reassuring green state. The database refuses to manufacture the false all-clear; the UI must not
   reintroduce it.
2. **A referral carries its reason** (`chk_pnc_referral`). A postnatal referral with no recorded reason
   is a woman sent onward with nothing said about why.
3. **`NOT_ASSESSED` ≠ `NONE` ≠ blank** for breastfeeding. "We did not look" is not "she is not
   breastfeeding".
4. **Postpartum family planning REUSES the antenatal or existing plan** — it points at the
   contraceptive episode rather than re-counselling from scratch. A UI that re-asks everything trains
   women to expect the visit to be long, and they stop coming.
5. **Bereavement suppression may never suppress a CRITICAL maternal signal.** A woman after a
   stillbirth is at *higher* risk of PPH, sepsis and VTE. A suppression trigger is never inferred from
   missing data.
6. **Offline is first-class.** `client_offline_id` is unique per tenant, so a replayed packet returns
   the **existing** contact rather than minting a duplicate visit. The UI must send a stable id per
   captured visit and treat a repeat response as success, not as a new record.
7. **HOME, COMMUNITY and VIRTUAL are first-class settings**, not a facility visit with a flag. Do not
   default the setting to FACILITY.

## 5. Payload keys

camelCase request, snake_case response. Deserialise the literal JSON in tests — a snake_case request
record against a camelCase client is a silent 400 indistinguishable from a validation failure.

## 6. The W12 surface, and why its path cannot be renamed

`POST /internal/v1/confidential/community/postnatal/contacts` and
`GET …/contacts/{motherCpid}`, composing `PostnatalContactService`.

**The `/confidential/` segment is load-bearing.** A contact carrying contraception content is stamped
`SEXUAL_REPRODUCTIVE_HEALTH`, and tshepo-authz classifies confidentiality from the **path the client
calls**. Mounted anywhere else this route is handed no confidential category, and after the governance
flip pct's fail-closed guard withholds the contact from the CHW who recorded it — while the service
stays green and the tests pass. `scripts/guard/check-confidential-lane-routing.sh` fails the build if
it moves.

Note the asymmetry the stamper already implements: a **routine** PNC visit is deliberately unstamped,
so only visits carrying contraception content become confidential. The UI should not assume every
postnatal record is protected, nor that none is.

**Statuses, and why the difference matters offline:**

| Status | Meaning | What the app does |
|---|---|---|
| **201** | contact recorded | clear the outbox entry |
| **200** | this `clientOfflineId` was already applied | clear the outbox entry — **success, not a duplicate** |
| **422** | pct refused it, with the reason: no `contactSetting`, or a referral with no reason | show the reason; it is something she can fix in the field |
| **502** `PCT_UNAVAILABLE` | could not reach the record | **re-send the same packet with its original `clientOfflineId`** |

That last row is the one to get right. A CHW told "the service is down" after a submit does not know
whether the visit landed. Re-sending the same packet is safe *because* the offline id makes the replay
idempotent; telling her to re-enter the visit creates exactly the duplicate the id prevents. And an
empty read is never an absence of contacts — it may be a withholding, and the two are indistinguishable
on purpose.

**Nulls are forwarded as nulls.** `screeningComplete`, `dangerSignsPresent` and `breastfeedingStatus`
pass through unfilled, and `contactSetting` is never defaulted to FACILITY. The BFF does not supply any
of them, because every available default is the reassuring one the schema exists to refuse.

## 7. Deliberately not here

The newborn's own postnatal pathway (that is the paediatric pack's, reached through the shared
pregnancy episode), MPDSR, and anything requiring the confidentiality flip.

## 8. Definition of done

A CHW, offline, can record a day-3 home visit for a woman registered from zero: complete the danger
sign screen, record breastfeeding honestly including `NOT_ASSESSED`, raise a referral with a reason,
and sync later without creating a duplicate visit — with no screen rendering an unanswered question
as a negative.
