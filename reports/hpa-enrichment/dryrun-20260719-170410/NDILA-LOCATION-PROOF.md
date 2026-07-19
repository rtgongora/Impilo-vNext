# HPA → Ndila location/coordinate consumer — rig proof (real data)

Throwaway Postgres loaded with the **real live-preview ndila schema**, over the **real 6,327 feed**.
Mirrors `HpaLocationImportService` (the committed Java is the production path): assert address/locality/
province as UNVERIFIED `ndila_locations` (owner `TUSO/FACILITY`, keyed to the tuso facility via
`HPA-<id>`, `source=IMPORT`); pass any candidate coordinate through `NdilaCoordinateValidator`; queue
`ndila_geocode_review_queue` for facilities without a plausible, corroborated coordinate.

## Results (reproducible)
| metric | value |
|---|---|
| facilities in feed | 6,327 (2,294 with a corroborated legacy address) |
| locations asserted (UNVERIFIED) | **6,322** (5 lacked province/name) |
| geocode-review queued (`MISSING_COORDINATES`) | **6,327** |
| locations with a public coordinate pin | **0** — nothing published unverified |
| text-searchable without coordinates | **6,322** |
| idempotency (2nd run) | **0 new** |

## Coordinate validator (mirrors NdilaCoordinateValidator — ZW bbox lat −22.5..−15.6, lng 25.2..33.1)
| candidate | verdict |
|---|---|
| Harare (−17.83, 31.05) | PLAUSIBLE_PIN |
| Bulawayo (−20.15, 28.58) | PLAUSIBLE_PIN |
| null-island (0,0) | REJECT_ZERO |
| London (51.5, −0.12) | REJECT_OUTSIDE_ZW |
| ocean (−30, 40) | REJECT_OUTSIDE_ZW |

## What this proves
- Address/locality/province imported as UNVERIFIED locations — facilities are **text-searchable
  without coordinates** ("Map location awaiting confirmation").
- **No unverified map pin is ever published** (0 coordinate pins); every facility lacking a plausible,
  corroborated coordinate is queued for confirmation.
- The coordinate validator accepts real Zimbabwe coordinates and **rejects null-island / out-of-country**
  coordinates — so the ~5,445 registration-keyed legacy geo rows cannot become pins by registration
  number alone.
- Idempotent re-run creates nothing.
