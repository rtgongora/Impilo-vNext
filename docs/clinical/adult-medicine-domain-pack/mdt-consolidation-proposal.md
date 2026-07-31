# MDT consolidation proposal: V051 owns the session, V114 owns the decision

**Status:** proposed. Not yet actioned against `V051__mdt_board_sessions.sql`, which is owned by
the telemedicine lane (TM-B15) and is **not altered by this document or by anything in the Adult
Medicine pack**. This proposal is the artefact the handover brief asked for before taking the
question to that lane; it records the evidence and the one rule, and stops there.

## 1. The finding

Two tables in `services/pct-service`, both reachable through their own controller, BFF client
method and UI page, and both plausibly "the MDT record":

| | `pct_mdt_sessions` / `pct_mdt_case_items` | `pct_mdt_decisions` / `pct_mdt_decision_problems` |
|---|---|---|
| Migration | `V051__mdt_board_sessions.sql` (telemedicine lane, TM-B15) | `V114__consultation_and_mdt.sql` (this pack, brief.md §14) |
| pct-service controller | `MdtController` → `/v1/mdt/**` | `ConsultationController` → `/v1/consultations/mdt` |
| BFF controller | `TeleconsultController` → `/internal/v1/teleconsult/mdt/**` | `ConsultationsController` → `/internal/v1/consultations/mdt` |
| BFF client | `PctServiceClient.mdtCreateSession` / `mdtGetBoard` / `mdtAddCase` / `mdtRecordOutcome` | `PctServiceClient.listMdtDecisions` / `recordMdtDecision` |
| UI | `/work/telemedicine/mdt` and `/work/telemedicine/mdt/[boardId]` | `/ehr/[patientId]/consultations` (`ConsultationShell`, "Multidisciplinary decisions") |
| First committed | predates V114 by weeks | this pack |

Both surfaces are live. A clinician who has just chaired an MDT for a patient has two places that
each look like the place to put the outcome, and nothing in either surface says the other exists.
That is a duplicate system of record under the estate's plainest guardrail, and — because V114 is
this pack's own migration — the duplicate is one this pack created, not one it inherited.

## 2. Column-level evidence that they model different things

They are not the same table twice; a straight merge would lose real, differently-shaped
information from each side. Reading the columns against each other:

| Question | V051 answers it with | V114 answers it with |
|---|---|---|
| What is this anchored to? | `referral_id NOT NULL REFERENCES pct_referrals` — a single telemedicine referral | `journey_id` / `encounter_id` / `episode_id`, at least one required (`pct_mdt_decisions_anchor_check`) — the patient's care continuum, not a referral |
| Who was in the room? | `chair_id NOT NULL`, `scribe_id` (nullable), no cast list beyond that | `participants TEXT NOT NULL` (free text, but attribution is enforced — see below), `chaired_by VARCHAR(255) NOT NULL` |
| What did the room produce? | `recommendation TEXT` (nullable) + `positions JSONB DEFAULT '[]'` — a structured per-participant `{participant, position: AGREE|DISSENT, note}` array | `decision TEXT NOT NULL`, `treatment_intent` (`CURATIVE`/`LIFE_PROLONGING`/`SYMPTOM_DIRECTED`/`DIAGNOSTIC`/`NOT_SET`, nullable-but-never-defaulted), `rationale TEXT` |
| What happens next? | Consensus package is written back onto the referral via `ReferralRepository`'s respond path — the *referral* lifecycle carries the next step | `next_action TEXT`, `responsible_service VARCHAR(64)` — explicit columns on the decision itself |
| What was discussed? | One referral per case item (`ordinal` = agenda position; one row per case) | `pct_mdt_decision_problems` — a join table to `pct_problems`, so a single decision can name several problems and a problem's history can be queried back to every decision that touched it |
| Who is allowed to see it? | `identity_visibility VARCHAR(32) DEFAULT 'PSEUDONYMISED'` enforced server-side at read time (non-privileged viewers get "Case N", no `referral_id`, no `patient_cpid` — see `MdtBoardService.getBoard`) | No visibility policy; the consultations screen is already inside the patient's own record, so pseudonymisation is not this table's concern |
| Is attribution enforced? | `chair_id NOT NULL`; `recommendation`/`positions` are **nullable** — a session can exist, even be closed, with no recorded outcome | `pct_mdt_decisions_attribution_check`: `participants`, `chaired_by` and `decision` must all be non-blank. A decision row cannot exist half-formed |
| Meeting type / clinical topic | `specialty VARCHAR(120)` (free text) | `meeting_type` constrained to an enum (`ONCOLOGY`, `CANCER_SITE_SPECIFIC`, `COMPLEX_MEDICINE`, `MORBIDITY_AND_MORTALITY`, `PALLIATIVE`, `TRANSPLANT`, `INFECTION`, `OTHER`) |

The pattern across every row: **V051 is built around convening and running a meeting** — an
agenda, a chair, live consensus/dissent capture, and a confidentiality policy for who is in the
room versus who reads the record afterwards. **V114 is built around an attributable clinical
decision** — one that sets treatment intent, survives independently of any particular meeting
having occurred, and links forward to the problem list rather than back to a referral.

Two further, smaller facts that a merge would have to reconcile and are worth naming so the
telemedicine lane isn't surprised by them later:

- `pct_mdt_case_items.recommendation`/`positions` are **nullable**; `pct_mdt_decisions.decision` is
  **NOT NULL and attribution-checked**. A schema that tried to be both would either weaken V114's
  guarantee or force every convened board to produce a decision, which is not true — boards defer
  cases.
- The two sides don't even share a foreign-key path today. `pct_referrals.encounter_id` is
  `VARCHAR(128)` (`V008`); `pct_mdt_decisions.encounter_id` is `UUID` (`V114`); a third encounter
  reference elsewhere in this same file (`pct_telehealth_sessions`, `V009`) is `BIGINT`. Linking
  the two tables by encounter would require picking a canonical encounter type first, which is a
  larger and separate problem than this proposal is trying to solve.

## 3. The proposed rule

**V051 owns the session. V114 owns the decision. A board's recommendation is not itself the
decision that sets treatment intent — it is input to one.**

Concretely:

1. `pct_mdt_sessions` / `pct_mdt_case_items` remain the system of record for *whether a chaired
   board met, who chaired it, what the agenda was, and what the panel's consensus and dissent were
   in the room*. `recommendation` and `positions` are the board's own minutes. Nothing outside
   `MdtBoardService` should ever read them as the authoritative clinical decision, because they can
   be absent (a case can be deferred with no recommendation recorded) and carry no treatment intent.
2. `pct_mdt_decisions` / `pct_mdt_decision_problems` remain the system of record for *the
   attributable decision that follows* — the one with an author, a chair, a treatment intent and a
   next action — whether it followed a formal chaired board, a ward-round discussion, or a corridor
   conversation between three consultants. This is deliberately broader than "output of a V051
   session": most MDT decisions in an under-resourced service will never go through a scheduled,
   minuted board, and a decision record that only worked for the formal case would still leave the
   §23.7 gap open for everything else.
3. **The explicit link, added the next time the telemedicine lane opens a migration in this
   territory:** a nullable `case_item_id UUID REFERENCES pct_mdt_case_items(case_item_id)` column
   on `pct_mdt_decisions`. Populated when a decision is the governed outcome of a specific board
   case; left null when the decision did not follow a formal board. The link is one-directional —
   `pct_mdt_case_items` never gains a `decision_id`, because a single case can generate zero
   decisions (deferred), one (the ordinary case), or in principle more than one over time (a
   decision revisited at a later board after new information), and a case-owned foreign key could
   only ever point at one of them.
4. Until that column exists, the two records can still be associated by hand: `pct_referrals` and
   `pct_mdt_decisions` both carry a patient identifier (`patient_cpid` and `subject_cpid`
   respectively), so a decision can be cross-referenced to the referral that prompted it by patient
   and date even without a formal FK.

This is a proposal to the telemedicine lane, not an instruction. **`V051` is not altered here** —
no migration in this pack adds the `case_item_id` column, and none is planned until the lane that
owns V051 has agreed to the direction.

## 4. What changed in this pack while the proposal is pending

Two acknowledgements were added so a clinician on either surface at least learns the other exists,
without adding a second write path to either record:

- `/work/telemedicine/mdt` (the board landing page) now states, next to "Convene a board", that a
  board's recommendation is not itself the treatment-intent decision, and that the decision is
  recorded on the patient's own record. It is a static note — the landing page has no patient
  context to link into.
- `/work/telemedicine/mdt/[boardId]` links each case item, once its referral has resolved to a
  patient CPID (i.e. for a privileged viewer — pseudonymised viewers still see nothing that could
  identify the patient), through to that patient's `/ehr/[patientId]/consultations` page, where the
  governed decision belongs.
- `ConsultationShell`'s "Multidisciplinary decisions" section (`/ehr/[patientId]/consultations`)
  now states that a chaired board is convened separately at `/work/telemedicine/mdt`, and links
  there, so a clinician recording a decision knows where the meeting itself — agenda, chair,
  consensus/dissent — is minuted if one took place.

No new table, endpoint, or write path was added by this pack. `recordMdtDecision` already existed
in the BFF (`ConsultationsController` → `PctServiceClient.recordMdtDecision`) before this proposal;
recording a decision from the consultations screen itself (there is currently no form for it, only
a read list) is `history-writes`-adjacent follow-up work and is out of scope here.

## 5. Corrected claim elsewhere in this pack

`ui/one-ui-shell/src/features/medicine/specialties/specialty-config.ts` listed, under oncology's
`notBuilt`, "MDT record (§14 is not built)". §14 is built — `pct_mdt_decisions`, its API, BFF and
UI all exist and are exercised by `consultation-shell.test.tsx`/runtime proof — the actual gap is the one
this document describes (a second, older record with a different shape) plus the missing "record a
decision" form. The line has been corrected to describe what is actually outstanding rather than
restate the "not built" claim `handover.md` §4 already flagged as an example of trusting an
uncorroborated CANNOT line.
