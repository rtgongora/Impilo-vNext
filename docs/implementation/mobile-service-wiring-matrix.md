# Mobile Service Wiring Matrix

Canonical sovereign services for Impilo vNext mobile (citizen + provider apps).  
Source of truth: `apps/mobile/packages/mobile-registry/`.

| Service | Citizen routes | Provider routes | API clients | Backend | Auth context | Wiring | Offline |
|---------|----------------|-----------------|-------------|---------|--------------|--------|---------|
| **Vito** | personal/health-id, auth/sign-up | — | clientRegistryService, healthIdService, profileService | vito-service, experience-bff | client | partiallyWired | cached profile shell |
| **Varapi** | — | professional/profile, auth/activation | identityRegistryService | varapi-service, experience-bff | provider | partiallyWired | session only |
| **Tuso** | home/facilities | auth/select-facility | facilityService | tuso-service, experience-bff | provider, facility | partiallyWired | facility list cache |
| **Tshepo** | personal/consent | tools/clinical | mobile-trust, consentService | tshepo-service (via BFF) | client, provider | partiallyWired | trust headers offline queue |
| **Butano** | personal/records, results | patients, results | recordsService, labResultService | butano-service, experience-bff | client, provider | partiallyWired | read cache where implemented |
| **Ubomi** | marketplace/ubomi-crvs | — | ubomiService | ubomi-service | client | fullyWired | requires connection |
| **Zibo** | — | tools/admin-registry | registryOperationsService | zibo-service | provider, admin | partiallyWired | requires connection |
| **Msika** | marketplace, health-os-apps | apps/health-os | marketplaceService, healthOsLauncherService | msika-service | client, provider | fullyWired | launcher cache |
| **Indawo** | home/facilities | outreach/field-tasks | facilityService, ndilaClient | indawo-service | client, provider | fullyWired | map tiles cached |
| **PCT** | telehealth, appointments | queue, patients, encounter | telehealthService, encounterService, clinicalWorklistService | pct-service | client, provider | partiallyWired | telehealth requires connection |
| **Costa** | personal/finance | tools/finance | financeService (blocked pending charges) | costa-service | client, provider | backendMissing | blocked state shown |
| **MusheX** | personal/finance, wallet | tools/finance | walletService, financeService | mushex-service | client, provider | partiallyWired | wallet read cache |
| **Oros** | — | tools/lab, pharmacy, madi_orders | labService, prescriptionService | oros-service | provider | partiallyWired | requires connection |
| **Simba** | personal/wellness | supervisor/stock | wellnessService, inventoryService | simba-service | client, provider | partiallyWired | wellness local cache |
| **Ndila** | home/facilities | outreach maps | mobile-ndila | ndila-service | client, provider | fullyWired | offline queue |
| **Nhume** | home/track-delivery | courier mode | nhumeService, deliveryService | nhume-service | client, provider | fullyWired | tracking requires connection |
| **Fundo** | marketplace/fundo | apps, tools/fundo | fundoLearningService | fundo-service | client, provider | fullyWired | content cache |
| **Nompilo** | global FAB | global FAB | mobile-nompilo | llm-orchestration | client, provider | partiallyWired | deterministic fallback |
| **Madi** | personal/madi-donor | tools/madi_* | madiService | madi-service | client, provider | fullyWired | requires connection for orders |
| **PACS** | personal/results | tools/pacs | PACSViewerScreen | pacs-adapter | client, provider | partiallyWired / deepLink | requires connection |
| **Live** | personal/impilo-live | tools/impilo_live | impiloLiveService | live-service | client, provider | fullyWired | VOD cache where available |

## State behaviour (all services)

- **Loading**: `LoadingSpinner`, `SkeletonLoader` on dashboards and section screens
- **Empty**: `EmptyState` with truthful copy (no fabricated rows)
- **Error**: `ErrorState` + `GlobalErrorBanner`; failed API does not crash app
- **Unauthorized**: `UnauthorizedState` when context/facility missing (provider work)
- **Offline**: `NetworkStatusBar`, `OfflineBanner`; live/telemedicine/PACS show requires-connection messaging

## Remaining gaps

1. **Costa** — pending charges / quotes BFF route not implemented; UI shows blocked badge
2. **PACS** — native DICOM viewer deferred; deep-link to web workspace
3. **Oros** — no standalone orders hub; routed via lab/pharmacy/MADI tools
4. **Tshepo/Butano** — indirect BFF proxy only (no direct service clients)
5. **Simba provider** — requisition create flow partial in supervisor mode
