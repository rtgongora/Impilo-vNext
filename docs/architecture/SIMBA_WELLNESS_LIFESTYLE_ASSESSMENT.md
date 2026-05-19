# Simba, Wellness, and Personal Health Data Assessment (May 2026)

## 1) Executive summary
- `simba-service` exists and is the canonical wellness/personal-health-data source-of-record (equivalent to PCT role for wellness).
- `wellness-service` remains as compatibility alias and transition surface where needed.
- Health Connect-style ingest exists in `wellness-service` and has been extended with source and permission governance so frontend surfaces can use real APIs instead of demo-only behavior.

## 2) Simba current functionality
- Provides wellness-adjacent APIs (`/internal/v1/wellness/*`) and ingest endpoints.
- Includes trust/JWT enforcement and persistence for wellness-related entities.
- Does not currently implement authoritative clinical workflow ownership.

## 3) Wellness/lifestyle current functionality
- Canonical implementation is on Simba runtime wellness APIs (activities, vitals, sleep, diet, goals, clubs, challenges, profile, personal-data source governance).
- Compatibility wellness-service endpoints can proxy/alias during migration, but ownership remains Simba.

## 4) Health Connect / HealthKit-equivalent current functionality
- Android Health Connect-style parity is implemented via `wellness/connect/v1` manifest and typed changesets.
- Source-neutral metadata now supported through connected source records and permissions.
- Apple/SDK-native runtime adapters are still pending owner-approved integration.

## 5) Personal health data model
- Canonical storage includes wellness activity rollups, vitals logs, mood logs, HC ingest logs, sleep segments, exercise sessions, extension JSON.
- Added source governance tables:
  - `wellness_connected_sources`
  - `wellness_source_access_audit`
  - `wellness_remote_alerts`

## 6) Source/device abstraction
- Supported source categories include manual entry, Android Health Connect, Apple HealthKit placeholder, wearable, IoT, remote monitoring device, provider/community entry, import/API.
- Source records include status, sharing scope, provider-access and writeback flags, category permission JSON.

## 7) Consent/sharing model
- Per-source flags now include `provider_access_allowed`, `clinical_writeback_allowed`, `consent_status`, and `sharing_scope`.
- Permission updates are auditable through `wellness_source_access_audit`.

## 8) Frontend surfacing
- `ui/one-ui-shell` Health Connect page now calls real source list/connect/permission APIs and real summary API.
- Remaining mock-heavy wellness pages (diet/programmes/routes/clubs/goals variants) remain backlog and must be progressively re-wired.

## 9) Backend/API status
- Added personal data APIs:
  - `GET/POST /internal/v1/wellness/personal-data/sources`
  - `PATCH /internal/v1/wellness/personal-data/sources/{id}/permissions`
  - `GET /internal/v1/wellness/personal-data/permissions`
  - `POST /internal/v1/wellness/personal-data/readings/manual`
  - `GET /internal/v1/wellness/personal-data/summary`
  - `GET /internal/v1/wellness/personal-data/provider-summary`
  - `GET/POST /internal/v1/wellness/personal-data/remote-alerts`
  - `POST /internal/v1/wellness/personal-data/remote-alerts/{id}/review`

## 10) Contract status
- `contracts/openapi/wellness.openapi.yaml` updated to include personal-data source/permission/manual-reading/summary/alert APIs.
- `simba.openapi.yaml` was expanded in this run to close parity for implemented wellness endpoints.

## 11) Integration status
- Experience BFF now supports both `WELLNESS_BASE_URL` and `WELLNESS_SERVICE_BASE_URL` to reduce routing drift.
- Mobile provider vitals route mismatch was corrected to use `/internal/v1/mobile/provider/vitals`.
- Wellness query compatibility improved for `person_cpid` alias handling in BFF wellness controller/client.

## 12) Security/audit status
- Personal-data APIs are authenticated under `wellness-service` security config.
- Source access/permission changes and manual reading writes emit source audit rows with actor and correlation context where provided.

## 13) Duplication/boundary risks
- Simba and wellness alias still overlap operationally during migration, but canonical ownership is now explicit:
  - Simba = canonical wellness and PGHD source-of-record.
  - Wellness-service = compatibility alias.

## 14) What was implemented in this run
- Source registry + permissions + access audit model.
- Manual reading API.
- Provider/citizen wellness summary API.
- Remote alert CRUD/review API.
- Health Connect UI source-management wiring.
- BFF env compatibility fix and provider mobile vitals path/wiring fixes.

## 15) What remains
- Complete non-demo wiring for all wellness frontend pages.
- Full explicit TSHEPO consent-policy roundtrip integration for category-level consent decisions.
- Structured BUTANO writeback acceptance workflow for clinically promoted wellness observations.

## 16) Owner decisions needed
- Decision made: Simba is the canonical wellness/personal-health-data domain service; wellness-service is alias compatibility only.
- Decision made: personal wellness data stays non-clinical by default; BUTANO writeback requires explicit provider acceptance workflow.
- Decision made: source-level sharing and category permissions are enforced through wellness permission APIs and audit trails in this phase.

## 17) Recommended architecture
- Simba: Ring 1 wellness execution owner and personal-health-data source-of-record.
- Wellness-service: compatibility alias during migration; no new ownership.
- BUTANO: Clinically accepted writeback only.
- TSHEPO: policy/consent/authz/audit governance on all cross-boundary reads/writes.
