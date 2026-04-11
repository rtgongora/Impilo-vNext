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

## Docker

From repository root (after databases exist):

```powershell
docker compose -f docker-compose.runtime.yml build clinical-knowledge-platform
docker compose -f docker-compose.runtime.yml up clinical-knowledge-platform
```

## Operational notes

- Seed content under `V002__seed_edliz_national_demo.sql` is **engineering demo** text — replace after licensed PDF ingestion and clinical sign-off.
- Flyway schema `clinical` is created automatically (`spring.flyway.create-schemas=true`).
