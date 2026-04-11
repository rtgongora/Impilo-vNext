# Clinical Knowledge Platform — developer runbook

## Prerequisites

- PostgreSQL 16 with database `clinical_knowledge` (see `scripts/seed/init-databases.sql`).
- Java 21, Maven 3.9+.
- **EDLIZ 2025 PDF** (national reference): `docs/reference/edliz-2025/EDLIZ-2025-final-for-circulation.pdf` — must exist in the working tree for provenance alignment (Flyway `V003` updates `source_documents` to point at this path).

## Run the service locally

```powershell
cd services
mvn -pl clinical-knowledge-platform-service spring-boot:run
```

OpenAPI UI: `http://localhost:8270/swagger-ui.html`

Health: `http://localhost:8270/actuator/health`

## Example requests

Assistant (internal — trust headers optional in local):

```http
POST http://localhost:8270/internal/v1/clinical/assistant/ask
Content-Type: application/json

{
  "question": "What does EDLIZ recommend for asthma maintenance?",
  "role": "DOCTOR",
  "patient_context": {
    "diagnoses": ["ASTHMA"],
    "activeMedications": [
      {"genericName": "salbutamol", "route": "INHALATION", "daysOnTherapy": 14}
    ]
  }
}
```

Prescribing evaluation:

```http
POST http://localhost:8270/internal/v1/clinical/prescribing/evaluate
Content-Type: application/json

{
  "diagnoses": ["GONORRHOEA"],
  "proposedMedications": [{"genericName": "ceftriaxone", "doseMg": 250}]
}
```

### PDF → `source_sections` ingestion (EDLIZ reference)

Run from the **repository root** so the default relative PDF path resolves (`impilo.clinical.edliz.reference-pdf-path`). The seeded national document id is `a0000001-0001-4001-8001-000000000001` (see Flyway `V002` / `V003`).

**Summary** (counts of `pdf/*` vs all sections):

```http
GET http://localhost:8270/internal/v1/clinical/source/documents/a0000001-0001-4001-8001-000000000001/ingestion-summary
```

**First ingest** (no prior `pdf/` rows — uses configured PDF path and optional SHA verify):

```http
POST http://localhost:8270/internal/v1/clinical/source/documents/a0000001-0001-4001-8001-000000000001/ingest-pdf
Content-Type: application/json

{
  "verify_sha256": true
}
```

**Re-ingest** (replace only `section_path` rows under `pdf/`):

```http
POST http://localhost:8270/internal/v1/clinical/source/documents/a0000001-0001-4001-8001-000000000001/ingest-pdf
Content-Type: application/json

{
  "replace_pdf_sections": true,
  "verify_sha256": true
}
```

Override path (resolved against `CLINICAL_INGESTION_WORKING_DIR` when relative):

```json
{
  "pdf_path": "docs/reference/edliz-2025/EDLIZ-2025-final-for-circulation.pdf",
  "replace_pdf_sections": true
}
```

### Via Experience BFF (port 8160)

Same paths under the BFF base URL (`NEXT_PUBLIC_BFF_URL` / default `http://localhost:8160`). Send standard companion headers (`X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`) and a JWT with an **admin-equivalent** realm role (`SYSTEM_ADMIN`, `FACILITY_ADMIN`, or `DEVELOPER`); other authenticated roles receive **403** on `/internal/v1/clinical/source/**`.

Example:

```http
GET http://localhost:8160/internal/v1/clinical/source/documents/a0000001-0001-4001-8001-000000000001/ingestion-summary
```

Experience UI hooks: `useClinicalSourceIngestionSummary`, `useClinicalDefaultEdlizDocumentId`, `useIngestClinicalPdf` in `ui/experience/src/hooks/queries/useGuidance.ts`.

### Curation queue & Kafka outbox

After PDF ingest, up to **25** `knowledge_review_items` rows are created (`SOURCE_EXCERPT` payloads). Curators approve/reject via:

```http
GET http://localhost:8270/internal/v1/clinical/curation/review-items?status=PROPOSED
POST http://localhost:8270/internal/v1/clinical/curation/review-items/{id}/decision
Content-Type: application/json

{ "decision": "APPROVED", "reviewer": "curator-id", "notes": "" }
```

**Outbox:** `clinical.event_outbox` receives rows for recommendation traces, prescribing traces (when `record_trace: true`), overrides, pathway completion, and approved knowledge items. Set `CLINICAL_KAFKA_RELAY_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS` to publish to topics in `docs/contracts/kafka-clinical-guidance-events.md`.

**Knowledge admin UI:** `ui/knowledge-admin` (port **3021**) — BFF-proxied curation console for operators.

## Docker

From repository root (after databases exist):

```powershell
docker compose -f docker-compose.runtime.yml build clinical-knowledge-platform
docker compose -f docker-compose.runtime.yml up clinical-knowledge-platform
```

## Operational notes

- Seed content under `V002__seed_edliz_national_demo.sql` is **engineering demo** text — replace after licensed PDF ingestion and clinical sign-off.
- Flyway schema `clinical` is created automatically (`spring.flyway.create-schemas=true`).
