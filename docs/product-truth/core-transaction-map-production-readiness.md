# Core Transaction Map — Production Readiness

| Field | Value |
|-------|-------|
| Program | Full Experience Production Readiness |
| Journeys | 47 (all transaction-complete post Wave 5) |
| Contract source | `contracts/openapi/experience-bff.openapi.yaml` |

## Shell zones

| Zone | Actor | Landing (post Wave 1) |
|------|-------|----------------------|
| My Life / My Health | Citizen, self-registered client | `/home` (Personal tab), limited until verified |
| Work / My Professional | Provider, facility staff | `/provider-workspace` → facility guard if needed |
| Enterprise | National/provincial ops | `/enterprise` |
| Public Health | PH officers | `/public-health` |

---

## Service transaction maps

### 1. Client intake and registration

| Item | Detail |
|------|--------|
| Actors | Facility staff, citizen (self-reg) |
| Transaction | `ADMINISTRATIVE_HEALTH` — client registry intake |
| Start → End | Search/dedup → capture demographics → provisional or verified Health ID |
| BFF | `/internal/v1/client-registry/*`, `/internal/v1/vito/*`, `/internal/v1/registry/intake/*` |
| UI | `/registry/clients`, `/registry/clients/new`, `/registry/intake`, `/auth/register` |
| Guards | `auth`, `facility` (staff), role |
| Integrations | VITO → PCT, telehealth, orders, payments |
| Tests | `registry/clients/page.test.tsx`, citizen onboarding e2e |

### 2. Provider ID application

| Item | Detail |
|------|--------|
| Actors | Prospective provider |
| Transaction | `ADMINISTRATIVE_HEALTH` — provider registry onboarding |
| BFF | `/internal/v1/registry/providers/*`, council linkage |
| UI | `/registry/providers`, `/registry/providers/new`, `/registry/providers/verification` |
| Tests | `ProviderRegistryOnboardingOrchestrationRail.test.tsx` |

### 3. Provider ID login

| Item | Detail |
|------|--------|
| Actors | Licensed provider |
| Transaction | `ADMINISTRATIVE_HEALTH` — role activation |
| BFF | `/internal/v1/auth/*`, `/internal/v1/identity/providers` |
| UI | `/auth/login`, `/auth/resolving`, `/provider-workspace` |
| Tests | `resolve-post-login-destination.test.ts`, `provider-login-golden-thread.test.ts` |

### 4. PCT / telehealth / inpatient

| Item | Detail |
|------|--------|
| Actors | Provider, nurse |
| Transaction | `CLINICAL_ENCOUNTER`, `TELECONSULT` |
| BFF | `/internal/v1/queue/*`, `/internal/v1/teleconsult/*`, `/internal/v1/inpatient/*` |
| UI | `/queue`, `/telemedicine/*`, `/clinical/inpatient/*`, `/ehr/[patientId]/*` |
| Integrations | OROS orders, Costa billing, MusheX payment, BUTANO SHR |
| Tests | telemedicine session page test, inpatient hooks |

### 5. Simba / Wellness

| Item | Detail |
|------|--------|
| Actors | Citizen |
| Transaction | `WELLNESS` |
| BFF | `/internal/v1/wellness/*`, `/internal/v1/simba/*` |
| UI | `/wellness/*` |
| Tests | wellness coaching, screenings page test |

### 6. PACS

| Item | Detail |
|------|--------|
| Actors | Provider, radiologist |
| Transaction | `DIAGNOSTIC_IMAGING` |
| BFF | `/internal/v1/imaging/*` |
| UI | `/ehr/[patientId]/imaging`, viewer boundary |
| Tests | imaging hooks, no fake DICOM claims |

### 7. MADI

| Item | Detail |
|------|--------|
| Actors | Donor, blood bank staff, clinician |
| Transaction | `TRANSFUSION`, `BLOOD_BANK` |
| BFF | `/internal/v1/madi/*` |
| UI | `/madi/*` (30 routes) |
| Integrations | OROS, Simba, Nhume dispatch, haemovigilance |
| Tests | `madi-golden-thread.test.ts`, `MadiBloodLogisticsPanel.test.tsx` |

### 8. OROS

| Item | Detail |
|------|--------|
| Actors | Lab staff, ordering provider |
| Transaction | `LAB_ORDER` |
| BFF | `/internal/v1/lab-orders`, `/internal/v1/lab-worklists` |
| UI | `/lab/*` |
| Integrations | PCT encounter, MADI blood orders |
| Tests | `LabWorklistControllerTest`, `lab/worklist/page.test.tsx` |

### 9. MusheX

| Item | Detail |
|------|--------|
| Actors | Citizen, cashier, payer ops |
| Transaction | `PAYMENT`, `REMITTANCE` |
| BFF | `/internal/v1/wallet/*`, `/internal/v1/finance/*` |
| UI | `/wallet/*`, `/finance/*` |
| Tests | wallet e2e, mushex-platform page test |

### 10. Costa

| Item | Detail |
|------|--------|
| Actors | Billing clerk, provider |
| Transaction | `BILLING`, `CLAIM` |
| BFF | `/internal/v1/finance/costa/*`, billing workspace |
| UI | `/finance/costa/*`, `/finance/billing` |
| Tests | costa encounter page test |

### 11. Fundo

| Item | Detail |
|------|--------|
| Actors | Learner, provider (CPD) |
| Transaction | `LEARNING` |
| BFF | `/internal/v1/learning/v11/*` |
| UI | `/learning/*` |
| Tests | `fundo-learning-golden-thread.test.ts` |

### 12. Coverage

| Item | Detail |
|------|--------|
| Actors | Enrolment officer, member |
| Transaction | `COVERAGE_ENROLMENT` |
| BFF | `/internal/v1/coverage/*` |
| UI | `/coverage`, `/coverage/enroll`, `/coverage/member`, `/coverage/contracts` |
| Tests | coverage e2e, golden-thread |

### 13. Indawo / Public Health

| Item | Detail |
|------|--------|
| Actors | PH officer, inspector |
| Transaction | `PUBLIC_HEALTH_SURVEILLANCE` |
| BFF | `/internal/v1/public-health/*` |
| UI | `/public-health/*` |
| Integrations | Ndila maps, Coverage, Tuso |
| Tests | site registry Ndila integration |

### 14. Enterprise

| Item | Detail |
|------|--------|
| Actors | National/provincial ops |
| Transaction | `ENTERPRISE_OPERATIONS` |
| BFF | inventory, procurement, dispatch, Costa intel |
| UI | `/enterprise/*` |
| Tests | warehousing, charge-sheet page tests |

### 15. Impilo Live Events (47th journey)

| Item | Detail |
|------|--------|
| Actors | Citizen, CPD learner |
| Transaction | `WELLNESS` / live broadcast |
| BFF | `/internal/v1/live/*` |
| UI | `/live/discover`, `/live/replays`, room join |
| Tests | `impilo-live-events-golden-thread.test.ts`, e2e |

---

## Global shell transaction

| Step | Route | Evidence |
|------|-------|----------|
| Sign in | `/auth/login` | Hero logo, `NompiloHint` |
| Resolve | `/auth/resolving` | `resolvePostLoginDestination` |
| Work context | `/provider-workspace` | Facility guard, task queues |
| Nompilo | Taskbar `Ctrl+K` | No AppLayout command strip |
