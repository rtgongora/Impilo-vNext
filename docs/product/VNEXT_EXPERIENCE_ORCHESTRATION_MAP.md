# vNext Experience Orchestration Map

> Generated: 2026-06-07T08:44:44.353Z
> Entries: **956**

JSON: [experience-orchestration-map.json](../../reports/product/experience-orchestration-map.json)

## Status summary

| Status | Count |
|--------|------:|
| coherent | 165 |
| partial | 144 |
| isolated-page | 43 |
| orphan-backend | 188 |
| orphan-frontend | 27 |
| mock-stub | 9 |
| unclear-intent | 54 |
| missing-journey | 326 |

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
| /core-transaction | provider | provider-patient-encounter | coherent |
| /client-journey | platform | core-transaction-orchestration | coherent |
| /provider-workspace | platform | core-transaction-orchestration | coherent |
| /platform-journey | platform | core-transaction-orchestration | coherent |
| /clinical-tools | provider | outpatient-consultation | missing-journey |
| /clinical-tools/rules | provider | outpatient-consultation | missing-journey |
| /clinical-tools/forms | provider | outpatient-consultation | mock-stub |
| /clinical/control-tower | provider | outpatient-consultation | missing-journey |
| /clinical/dictation | provider | outpatient-consultation | missing-journey |
| /clinical/emergency | provider | outpatient-consultation | missing-journey |
| /clinical/inpatient | provider | outpatient-consultation | missing-journey |
