# vNext Experience Orchestration Map

> Generated: 2026-06-08T14:34:53.641Z
> Entries: **965**

JSON: [experience-orchestration-map.json](../../reports/product/experience-orchestration-map.json)

## Status summary

| Status | Count |
|--------|------:|
| coherent | 430 |
| partial | 117 |
| isolated-page | 43 |
| orphan-backend | 189 |
| orphan-frontend | 28 |
| mock-stub | 7 |
| unclear-intent | 41 |
| missing-journey | 110 |

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
| /auth | citizen | citizen-onboarding | coherent |
| /auth/register | citizen | citizen-onboarding | coherent |
| /auth/register/assurance | citizen | citizen-onboarding | coherent |
| /auth/register/status | citizen | citizen-onboarding | coherent |
| /auth/resolving | provider | provider-login | coherent |
| /privacy | citizen | — | unclear-intent |
| /terms | citizen | — | isolated-page |
| /consent | citizen | consent-capture | coherent |
| /account-deletion | citizen | — | isolated-page |
| /privacy/app-stores | citizen | — | unclear-intent |
| /clinical | provider | outpatient-consultation | coherent |
| /core-transaction | provider | provider-patient-encounter | coherent |
| /client-journey | platform | core-transaction-orchestration | coherent |
| /provider-workspace | platform | core-transaction-orchestration | coherent |
| /platform-journey | platform | core-transaction-orchestration | coherent |
| /clinical-tools | provider | outpatient-consultation | missing-journey |
| /clinical-tools/rules | provider | outpatient-consultation | missing-journey |
| /clinical-tools/forms | provider | outpatient-consultation | mock-stub |
| /clinical/control-tower | provider | outpatient-consultation | coherent |
| /clinical/dictation | provider | outpatient-consultation | coherent |
| /clinical/emergency | provider | outpatient-consultation | coherent |
| /clinical/inpatient | provider | outpatient-consultation | coherent |
