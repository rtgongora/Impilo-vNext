# Clinical Knowledge and Guidance Platform

## Purpose

National, governed clinical knowledge anchored on MoHCC sources (EDLIZ and successors). The platform answers conversational questions, powers prescribing decision support, pathways, nudges, and audit — without ungoverned free-form “medical chatbot” behaviour when validated sources or rules are missing.

## Deployable topology (v1)

| Deployable | Port (local) | Responsibility |
|------------|----------------|----------------|
| `clinical-knowledge-platform-service` | 8270 | Source-backed retrieval, structured medicine rows, deterministic rules, assistant orchestration, prescribing evaluation, pathways, nudges, recommendation traces |
| `guidance-service` | 8260 | General wellness guidance, reminders, education articles (Health OS §13) |
| `experience-bff` | 8160 | Proxies `/internal/v1/clinical/**` to the platform |

Bounded contexts live as **Java packages** inside `clinical-knowledge-platform-service`, ready to split into separate Maven modules or services without changing domain language:

- `clinical.source` — documents & sections (Flyway schema `clinical`)
- `clinical.assistant` — question routing, retrieval composition, trace IDs
- `clinical.rules` — deterministic `ClinicalRulesEngine`
- `clinical.prescribing` — regimen metadata + rule merge
- `clinical.pathway` — pathway definitions & sessions
- `clinical.nudge` — proactive evaluation (rules-first)
- `clinical.audit` — `recommendation_traces`, overrides

## National reference PDF (in-repo)

Canonical file: `docs/reference/edliz-2025/EDLIZ-2025-final-for-circulation.pdf` (index: `docs/reference/edliz-2025/README.md`). The clinical platform’s `source_documents.file_reference` and `content_hash` are aligned via migration `V003__edliz_2025_reference_bundle.sql`. Runtime path override: `EDLIZ_REFERENCE_PDF_PATH` / `EDLIZ_REFERENCE_SHA256`.

## Data authority

1. **Source sections** are authoritative evidence for citations.
2. **Structured rows** (`medicine_guidance`, future `condition_guidance`) back dosing and policy metadata.
3. **Java rule pack** encodes first-wave safety logic; DB `rule_definitions` carry human-readable templates and versioning for alignment (executable logic can move to decision tables later).

## Integration

- Experience UI: **Ask** page toggle “Ask EDLIZ” → `POST /internal/v1/clinical/assistant/ask` via BFF.
- Prescribing UI (future): `POST /internal/v1/clinical/prescribing/evaluate` with `diagnoses`, `activeMedications`, `proposedMedications`, `facilityLevel`, stewardship fields.

## Safety modes

Responses expose `support_mode`: `SOURCE_GROUNDED`, `RULE_EVALUATED`, `MIXED_RULE_AND_SOURCE`, `INSUFFICIENT_EVIDENCE`. Citizen mode avoids prescriber-only pathways.

## Related documents

- `docs/adr/ADR-0042-clinical-knowledge-monolith-to-services.md`
- `docs/runbooks/clinical-knowledge-platform-dev.md`
- `docs/contracts/kafka-clinical-guidance-events.md`
- `docs/clinical-governance/edliz-engineering-seed-policy.md`

## Future extension (target state)

- Split `ClinicalRulesEngine` into dedicated `clinical-rules-engine-service` with Drools or DMN for curator-edited tables.
- Keep `extraction_jobs` + `knowledge_review_items` aligned with the document OCR/PDF pipeline and the canonical Experience curator workflow (`/admin/clinical-curation`); legacy `ui/knowledge-admin` remains reference-only.
- Attach vector retrieval (`chunk_embedding_ref`) behind a feature flag; keep lexical search as default for auditability.
- Citizen channel: hard policy pack so `citizen_mode` never receives prescriber-only payloads.
