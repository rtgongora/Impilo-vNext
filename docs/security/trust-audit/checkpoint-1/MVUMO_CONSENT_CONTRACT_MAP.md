# Mvumo / consent contract map — Checkpoint 1

| Producer | Contract / API | Consumer | Live status |
|---|---|---|---|
| Mvumo UI/BFF | `/internal/v1/mvumo/**` lifecycle | mvumo-service | **PARTIAL** (capture) |
| mvumo-service | FHIR R4 Consent POST `/v1/consent` | tshepo-consent-service | **PARTIAL** (needs runtime proof of materialisation) |
| tshepo-consent-service | `GET /v1/consent/evaluate` | fhir-gateway (correct) | **ENFORCED** when on path |
| tshepo-consent-service | `GET /v1/consent/evaluate` | tshepo-authz ConsentClient | **DISCONNECTED** — client uses **POST** (broken) |
| Mvumo | `POST /internal/v1/mvumo/evaluate` | (tests only) | **DISCONNECTED** from production callers |
| Experience BFF | consent summary for display | EHR UI | **ABSENT** as gate |
| BUTANO / `/fhir` | none | SHR readers | **ABSENT** |
| Lawful bases | explicit consent | coded | **PARTIAL** |
| Lawful bases | emergency/break-glass | coded in PDP/gateway | **ACTIVE_NOT_ENFORCED** |
| Lawful bases | direct-care / statutory | — | **ABSENT** |

Canonical shared `ConsentDecision` / `LawfulBasisDecision` contracts: **ABSENT** (programme Checkpoint 2).
