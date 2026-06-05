# vNext Experience Orchestration Map

## Phase 4 completion update (2026-06-05)

- `/ehr/[patientId]/encounter/[encounterId]` — **orchestration-linked** via `EncounterOrchestrationRail` + `useEncounterCoreTransaction` (BFF `GET /internal/v1/core-transactions?encounter_id=`).
- Mobile `EncounterScreen` — transaction context card (read-only parity).


> Generated: 2026-06-05T07:37:40.190Z
> Entries: **872**

JSON: [experience-orchestration-map.json](../../reports/product/experience-orchestration-map.json)

## Status summary

| Status | Count |
|--------|------:|
| coherent | 104 |
| partial | 135 |
| isolated-page | 39 |
| orphan-backend | 175 |
| orphan-frontend | 27 |
| mock-stub | 9 |
| unclear-intent | 27 |
| missing-journey | 356 |

| route | actor | journey | status |
| --- | --- | --- | --- |
| /auth/login | provider | provider-login | coherent |
| /auth/login/email | provider | provider-login | coherent |
| /auth/login/provider-id | provider | provider-login | coherent |
| /auth/login/biometric | provider | provider-login | coherent |
| /auth/forgot-password | citizen | — | unclear-intent |
| /auth/reset-password | citizen | — | unclear-intent |
| /auth/mfa | provider | provider-login | coherent |
| /auth/logout | citizen | — | unclear-intent |
| /auth | citizen | citizen-onboarding | missing-journey |
| /auth/register | citizen | citizen-onboarding | missing-journey |
| /auth/register/assurance | citizen | citizen-onboarding | missing-journey |
| /auth/register/status | citizen | citizen-onboarding | missing-journey |
| /auth/resolving | provider | provider-login | coherent |
| /privacy | citizen | — | unclear-intent |
| /terms | citizen | — | isolated-page |
| /consent | citizen | consent-capture | coherent |
| /account-deletion | citizen | — | isolated-page |
| /privacy/app-stores | citizen | — | unclear-intent |
| /clinical | provider | outpatient-consultation | missing-journey |
| /core-transaction | provider | provider-patient-encounter | missing-journey |
| /client-journey | platform | core-transaction-orchestration | missing-journey |
| /provider-workspace | platform | core-transaction-orchestration | missing-journey |
| /platform-journey | platform | core-transaction-orchestration | missing-journey |
| /clinical-tools | provider | outpatient-consultation | missing-journey |
| /clinical-tools/rules | provider | outpatient-consultation | missing-journey |
| /clinical-tools/forms | provider | outpatient-consultation | mock-stub |
| /clinical/control-tower | provider | outpatient-consultation | missing-journey |
| /clinical/dictation | provider | outpatient-consultation | missing-journey |
| /clinical/emergency | provider | outpatient-consultation | missing-journey |
| /clinical/inpatient | provider | outpatient-consultation | missing-journey |
