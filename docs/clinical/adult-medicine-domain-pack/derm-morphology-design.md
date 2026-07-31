# Structured dermatology morphology — design note

**Status:** design before code. Dermatology specialty keeps `Structured morphology coding` on `notBuilt`.

## Problem

Examination framework (V113) can site skin findings and store free-text. It does not own a
governed morphology / distribution coding model (primary lesion, secondary change, configuration,
distribution). Photography-with-consent is a separate consent + document lane.

## Proposed model (future)

| Axis | Examples | Storage |
|---|---|---|
| Primary morphology | macule, papule, plaque, vesicle, bulla, pustule, nodule, ulcer | coded vocabulary |
| Secondary change | scale, crust, lichenification, scar | coded, multi |
| Configuration | discrete, confluent, linear, annular | coded |
| Distribution | face, acral, flexural, photodistributed, dermatomal | sites + laterality (reuse exam site) |

Bind to examination region `SKIN` / graphic codes; do not invent a parallel problem list.

## Non-goals

- Do not treat free-text "rash" notes as structured morphology
- Do not store third-party identifiable photos without consent document refs
- Do not shorten `notBuilt` until a write path + honesty tests exist

## Next build slice (when authorised)

1. Vocabulary + PCT append-only morphology observations keyed to encounter
2. UI picker on dermatology specialty assessment
3. Flip notBuilt only after live write + read honesty
