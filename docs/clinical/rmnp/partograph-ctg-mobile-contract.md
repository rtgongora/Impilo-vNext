# Partograph and CTG — contract for the mobile provider app

**Audience:** the mobile-recovery lane, as the handover artifact for wiring the governed Partograph
and CTG instruments in the provider app's specialty workspace.

**Status:** backend is built, deployed and live-proven. Form definitions are governed and seeded.
Nothing in this document needs new server work — it describes what already exists.

---

## 1. What this replaces, and why it is not a rebuild

The specialty workspace rendered "Partograph" and "CTG Interpretation" as a generic notes box. The
mechanism is worth understanding before wiring the replacement, because it explains why every other
instrument in the panel is wrong too: `SpecialtyWorkspacePanel.tsx` chooses a tool's form kind from
its **index in an array** —

```ts
if (index === 3) return NO_GENERIC_CALCULATOR_WORKSPACES.has(workspaceId) ? "soon" : "sum";
return "notes";
```

so "Partograph" is a notes box because it is first in `specialtyWorkspaces.ts`, not because anyone
decided a partograph is notes. That root defect is owned by the specialty-tools sweep; this document
covers only the two maternal instruments.

**Both already have a real backend.** pct-service migration `V056` has held the tables since before
this sweep, with a progress engine that evaluates rather than merely stores. The BFF routes were a
404 between commit `062d827c9` and their restoration, which is why the mobile panel had nothing to
call even if it had wanted to. They are live now.

> **Do not build persistence for these.** A second store for a labour observation would give a ward
> two partographs for one woman that could disagree about whether she crossed the action line.

---

## 2. Endpoints

All paths are on the experience BFF. Trust headers are forwarded automatically by
`ServiceClientConfig`; a service→service call mints its own `client_credentials` token.

### Partograph

| Method | Path | Notes |
|---|---|---|
| `POST` | `/internal/v1/maternity/partograph/sessions` | Open a session. PCT enforces one active per patient. |
| `GET` | `/internal/v1/maternity/partograph/sessions/active?patientId=&encounterId=` | See §4 — "none open" is a 200, not a 404. |
| `GET` | `/internal/v1/maternity/partograph/sessions/{sessionId}` | Session, plotted points and the current assessment. |
| `POST` | `/internal/v1/maternity/partograph/sessions/{sessionId}/points` | Record one observation. Returns the point **and** the progress assessment. |
| `POST` or `PATCH` | `/internal/v1/maternity/partograph/sessions/{sessionId}/close` | Both verbs accepted; both forward as PCT's `POST`. |

### CTG

| Method | Path | Notes |
|---|---|---|
| `POST` | `/internal/v1/maternity/ctg/sessions` | |
| `GET` | `/internal/v1/maternity/ctg/sessions/active?patientId=&encounterId=` | |
| `GET` | `/internal/v1/maternity/ctg/sessions/{sessionId}` | |
| `POST` | `/internal/v1/maternity/ctg/sessions/{sessionId}/chunks` | Device samples. Not a form. |
| `GET` | `/internal/v1/maternity/ctg/sessions/{sessionId}/chunks?channel=&from=&to=` | |
| `POST` | `/internal/v1/maternity/ctg/sessions/{sessionId}/annotations` | Clinician findings — this is what the form captures. |

---

## 3. Governed form definitions

Served from forms-service; do not hard-code the fields.

| Instrument | `formKey` | Seed |
|---|---|---|
| Partograph observation | `impilo.labour.partograph.observation.v1` | `13-labour-partograph-observation.json` |
| CTG annotation | `impilo.labour.ctg.annotation.v1` | `14-ctg-annotation.json` |

Both are `offlineCapable: true`, `sensitivity: SENSITIVE`, female + `requirePregnant` applicability.

**Only two fields are required on the partograph observation: the time and the stage of labour.**
That is deliberate. A partograph is filled in as observations are taken — a cervical examination
happens every few hours while the fetal heart is listened to every half hour — so a form demanding
every field at once would be abandoned or filled with guesses. Please do not add client-side
required validation beyond what the definition declares.

---

## 4. Six behaviours the UI must preserve

These are the reasons the backend is shaped as it is. Getting any of them wrong reintroduces a
defect that has already been fixed once.

**1. "No partograph is open" is an answer, not an error.**
`GET …/sessions/active` returns **200** with `{"partograph_active": false}` when nothing is open.
Only an inability to ask is a failure. Do not render the 200 as an error state, and do not treat a
502 as "no partograph".

**2. A 502 means the record could not be read — never that it is empty.**
Every one of these proxies returns `502` with `error.code = "PCT_UNAVAILABLE"` and a
`clinical_note` saying so, on an upstream failure *and* on a null payload. The UI must show that the
labour chart is unavailable. Rendering an empty partograph on a 502 tells a midwife that a labouring
woman has no observations, which is an affirmative clinical claim and a fabricated one.

**3. The progress assessment comes back with the write — surface it immediately.**
`POST …/points` returns the assessment alongside the saved point, deliberately: a reading that
crosses the action line has to reach the midwife who just recorded it, not wait for someone to
reopen the chart. Statuses are `LEFT_OF_ALERT`, `BETWEEN_ALERT_AND_ACTION`,
`AT_OR_RIGHT_OF_ACTION`, `SECOND_STAGE`, `LATENT_PHASE_NOT_ASSESSED`, `INSUFFICIENT_DATA`.

**4. `INSUFFICIENT_DATA` is not reassurance.**
An empty partograph reports `INSUFFICIENT_DATA` **and** lists every outstanding observation as never
recorded. It must never render as a calm or neutral state — it is the most alarming case, not the
least. A previous deploy shipped it beside "0 outstanding observations" and that read as an
all-clear on a chart nobody had filled in.

**5. Never carry a previous value forward.**
Cervical dilatation is recorded only when an examination was actually performed. Pre-filling the
last value draws a progress line nobody measured, and the alert-line origin is pinned at the first
active-phase reading precisely so a corrected later entry cannot silently rewrite whether the labour
ever crossed.

**6. A CTG gap is a gap.**
Chunks carry `missing_sample_count`. Where the transducer lost contact, plot a break — do not
interpolate and do not join across it. A flat line nobody measured looks identical on screen to one
that was, and only one of them means the fetal heart stopped.

---

## 5. Payload keys

PCT accepts **both** `snake_case` and `camelCase` for every multi-word key, verified by scanning
every accessor in `MaternityService`. The web shell posts camelCase (`cervicalDilationCm`) and reads
snake_case (`cervical_dilation_cm`) — that asymmetry is real and safe.

> If you add a typed request record anywhere in this path, give it `@JsonAlias` for both spellings
> and write the test by **deserialising the literal JSON the client sends** rather than constructing
> the record in Java. A snake_case record with a camelCase client binds every field to null and
> `@Valid` returns 400 before the request leaves the BFF — indistinguishable from a validation
> failure. That defect was live in the adult-medicine write path this week.

---

## 6. What is deliberately not here

- **Automated CTG categorisation.** Severity is the clinician's judgement. An automated category
  computed over a recording with gaps is a confident answer about data that was never captured.
- **APGAR, in this panel.** A working `APGARScreen` already posts to `/internal/v1/apgar`; the panel
  entry was a duplicate fake beside it and is being deleted rather than reimplemented.
- **Bishop Score.** LIVE as of RMNP W14-A — assessment-only cervical favourability via CKP
  `BishopScoreEngine` and form 22 (`impilo.maternal.bishop.v1`). Blank ≠ zero; nothing persisted
  on the score path (no new labour SoR).
- **Fenton.** Fenton is
  preterm growth and belongs to the paediatric lane, which owns the growth engine and the stamped
  standard-and-version discipline; a second growth implementation would give one preterm baby two
  growth histories that disagree the first time either standard is revised.

---

## 7. Definition of done

Per the fleet-wide rule this sweep adopted: **a replacement is not done when it renders and
persists — it is done when the thing it replaced can no longer be reached.** A governed partograph
alongside a still-reachable notes box is not a fix; it is two answers to the same question, one of
which is wrong.
