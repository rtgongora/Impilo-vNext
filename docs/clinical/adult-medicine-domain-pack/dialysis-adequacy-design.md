# Dialysis adequacy / modality / RRT prescription — design note

**Status:** design before code. Nephrology specialty `notBuilt` retains the RRT items until this lands.

## Problem

Catalogue procedures (HD / PD / AVF) and fluid-balance vitals exist on the shared spine.
They do not record:

- Dialysis **adequacy** (e.g. Kt/V, URR) as a clinical fact over time
- **Modality review** (HD ↔ PD ↔ conservative care) with decision, rationale, and date
- An **RRT prescription** (session length, dialyser, blood flow, dry weight target) as SoR

Treating the procedures catalogue as adequacy would invent a SoR.

## Proposed SoR (future)

| Concern | Owner | Notes |
|---|---|---|
| Adequacy measurements | PCT or a dialysis clinical module under clinical plane | Append-only; journey/encounter anchored when visit-scoped |
| Modality decision | PCT medical episode / care-plan intervention | Must not fork nephrology privately |
| Session prescription | Clinical SoR (not MSIKA logistics alone) | Fulfilment/scheduling may integrate later |

## Non-goals

- Do not build a private nephrology workflow that bypasses procedures-service for HD/PD/AVF cases
- Do not store adequacy inside telemonitoring as a silent rival without a problem-list anchor
- Do not shorten specialty `notBuilt` until the first write path exists

## Next build slice (when authorised)

1. Flyway tables for modality decision + adequacy observation (score-or-reason)
2. PCT API + BFF
3. Nephrology specialty panel + spineLinks
4. Remove the corresponding `notBuilt` rows only then
