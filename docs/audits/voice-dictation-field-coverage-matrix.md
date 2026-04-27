# Voice dictation — field coverage matrix (living)

**Purpose**: Track every **meaningful narrative** (or mixed) field across domains, dictation eligibility, implementation, governance, and tests.  
**Rule**: Structured coded fields stay structured; dictation targets **free text** (and optional “search then confirm” flows for codes — not auto-coded dictation).

**Legend — Dictation Required?**  
`Y` = should have dictation per doctrine | `N` = structured only | `M` = mixed (structured + narrative sub-field)

**Legend — Status**  
`DONE` | `GAP` | `N/A` | `STUB`

---

## Matrix (seed rows — expand per wave)

| Domain | Module | Route/Page | Component/File | Field/Form | Data Entry Type | Narrative or Structured? | Dictation Required? | Dictation Present? | Component Used | Provider | Review Required? | Audio Stored? | Mvumo Consent Required? | Audit/Event Required? | Status | Remaining Gap | Test Coverage |
|--------|--------|------------|------------------|-------------|-----------------|---------------------------|----------------------|---------------------|------------------|----------|------------------|---------------|------------------------|------------------------|--------|---------------|---------------|
| Experience | vitals | `/ehr/[patientId]/vitals` | `vitals/page.tsx` | Observation summary JSON | Text in JSON | M | Y | No | `<textarea>` | — | Y | No* | Optional | GAP | Wire `DictatableTextarea` or shell equivalent | Vitals page tests (no dictation) |
| Experience | vitals | same | same | Labour / partograph / CTG notes | Textarea | Narrative | Y | No | plain textarea | — | Y | No* | Optional | GAP | Same | Partial |
| Experience | clinical | `/clinical/dictation` | `clinical/dictation/page.tsx` | Transcript editor | Large text | Narrative | Y | Partial | manual + stub copy | — | Y | If cloud STT | Y if cloud | STUB | Connect to real STT + notes API | `page.test.tsx` |
| Shell | clinical | `/clinical/dictation` | one-ui-shell routes | Same | — | Narrative | Y | TBD | shell copy | Web Speech / optional API | Y | If API | Y if API | GAP | Align with shared-ui | TBD |
| Shell | components | shared pattern | `DictatableTextarea.tsx` | Any consumer | Textarea | Narrative | Y | Yes | `DictatableTextarea` | Browser (+ optional API) | Y | If API | Recommended | DONE | Rollout to fields | Hook tests TBD |
| EHR stub | encounter | local | `EncounterPanel.tsx` | Progress notes | Textarea | Narrative | Y | No | `<textarea>` | — | Y | No | Optional | GAP | Add dictation | None |
| PCT / OROS / Portal / Consoles | various | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | Populate per app audit | TBD |
| Mobile | citizen / provider | TBD | TBD | Free-text fields | Native | Narrative | Y | TBD | OS STT | OS | Y | Per platform | Y | GAP | Native bridge | TBD |

\*Browser-only Web Speech typically does **not** send raw audio to Impilo servers; **cloud STT** paths require explicit consent and architecture sign-off.

---

## How to extend this matrix

1. For each UI workspace (`ui/*`), run ripgrep: `textarea`, `contentEditable`, `placeholder=` for clinical/ops strings.  
2. For each row, assign **Mvumo Consent Required?** = `Y` if audio leaves device or transcript is processed by third-party.  
3. Link **Audit/Event** to `tshepo-audit-service` event type once standardised.  
4. Keep **Test Coverage** in sync with Vitest/Playwright IDs.
