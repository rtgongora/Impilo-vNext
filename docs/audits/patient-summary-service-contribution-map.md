# Patient summary — service contribution map

**BFF contract (representative):** `GET /internal/v1/summary/patient/{id}` aggregates multiple services. Exact shape is defined in BFF + OpenAPI.

| Concern | Service | In summary today | UI surface | Notes |
|---------|---------|------------------|------------|--------|
| Identity | VITO | Y (context) | Chart header | — |
| Clinical timeline | BUTANO / FHIR | Partial | ehr | Expand |
| Journey | PCT | Partial | ehr, summary | — |
| Orders / results | OROS | Target | ehr | Wire if missing |
| Consent / directives | Mvumo | Y | cards | — |
| **Finance snapshot** | COSTA / MusheX | **Target** | summary, `finance` | Add outstanding balance / claim status when BFF enriches |
| Imaging | PACS / adapter | TBD | chart | P1 |

**Change needed for COSTA/MusheX in summary:** extend BFF aggregation DTO + experience summary components when product prioritises.
