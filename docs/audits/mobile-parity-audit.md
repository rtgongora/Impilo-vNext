# Mobile parity audit — Impilo vNext

**Date**: 2026-04-10  
**Branch context**: `claude/staging-ux-orchestration-remediation-Yypyl` (per programme)  
**Auditor**: Engineering (repo inspection + doc crosswalk)

## 1. Purpose

Compare **web / One UI / Experience** capabilities with **Citizen** and **Provider** mobile surfaces under the rules in `docs/product/mobile-navigation-and-parity-doctrine.md`: same platform truth, no critical citizen/provider capability hidden only on web without justification, mobile-appropriate UX, offline/low-bandwidth honesty, no production fake clinical or financial data.

## 2. Repository map (evidence)

| Layer | Paths |
|-------|--------|
| Citizen app | `apps/mobile/citizen-app` — Expo, `CitizenTabs`, `HomeScreen`, `PersonalScreen` hub |
| Provider app | `apps/mobile/provider-app` — `ModeRouter`, `ProviderTabs`, `OutreachTabs`, `SupervisorTabs`, `OfflineTabs` |
| Shared mobile | `apps/mobile/packages/mobile-api-client`, `mobile-auth`, `mobile-offline`, `mobile-messaging`, `mobile-trust`, `mobile-timeline`, `mobile-design-system` |
| Web reference | `ui/one-ui-shell`, `ui/experience`, `ui/shared-ui` (trust + dictation contracts) |
| Consent / Mvumo backend | `services/tshepo-consent-service`, `services/mvumo-service` (skeleton in catalog), Experience BFF `/internal/v1/mobile/*` |

## 3. Platform truth alignment

| Concern | Web | Mobile | Assessment |
|---------|-----|--------|-------------|
| Trust headers | `shared-ui` / TSHEPO | `@impilo/mobile-trust` + api client | **Aligned** (same header names). |
| API base | BFF via gateway rewrites | `EXPO_PUBLIC_API_BASE_URL` → `configureApiClient` | **Aligned** (env-driven). |
| Auth | Keycloak | `expo-auth-session` + secure store | **Aligned** pattern. |
| Dictation types | `shared-ui/dictation` | RN uses OS/keyboard assist + future native STT | **Partial** — shared TypeScript contracts not yet imported in RN; acceptable short-term if BFF contracts match. |

## 4. Navigation vs One UI Shell

- **Web**: zone layout, sidebars, command palette (where present).
- **Mobile**: bottom tabs + **Launch** (`ProviderDashboardScreen`) / **Home** quick actions + **Clinical Tools** internal tabs + **Personal** pill navigator — **compliant** with doctrine (no desktop sidebar clone).

**Remediation in this pass**: Provider **Telemedicine** screen was implemented but **unreachable** from tabs; wired under Clinical Tools. **Citizen Support** screen existed but was **not** in Personal hub; added. **Launch** row for Comms / Telehealth / Support / safety guidance added on provider dashboard.

## 5. Capability summary (high level)

### 5.1 One UI / shell-like capabilities

| Capability | Citizen | Provider | Gap (pre-fix) | Post-fix / note |
|------------|---------|----------|----------------|-----------------|
| Start / launcher | `HomeScreen` | `ProviderDashboardScreen` | — | OK |
| Notifications badge | Home header | Launch tab badge | — | OK |
| Comms hub | Messaging tab | Messages tab | — | OK |
| Help / Nompilo | **Partial** (no dedicated product screen) | **Partial** | Web KB integration | Document **future**; use Support tickets |
| System support / tickets | **Missing from hub** | Supervisor escalations | Citizen gap | **Support + Consent** tabs in Personal |
| SOS | `EmergencySOSSection` | Supervisor / policy | Provider UX | Launch row + mode switch documented |
| Search | Marketplace / discovery | Patient lookup | Global search | **Future** |
| Role / facility context | Profile, facility picker | Chips on dashboard + `SelectFacility` flow | — | OK |

### 5.2 Clinical / EHR

| Capability | Provider evidence | Status |
|--------------|-------------------|--------|
| Patient lookup | `PatientLookupScreen` | OK |
| Queue | `QueueManagementScreen` + services | OK |
| Encounter | `EncounterScreen` + `encounterStore` | OK |
| Results | `ResultsViewScreen` | OK |
| SOAP / tools hub | `ClinicalToolsScreen` | OK (persist integration debt) |
| Telemedicine | `TelemedicineScreen` + API | **Was orphan** → fixed route |
| Referrals / care plans | Multiple provider screens | OK (API-bound) |

### 5.3 Mvumo / consent

| Capability | Citizen | Provider |
|------------|---------|----------|
| Preference / sharing toggles | `ConsentScreen`, `SettingsSection` | Partial (encounter switches) |
| Mvumo session / proof / remote response | **Requires BFF + mvumo-service** | Same |

**Classification**: **Required on both** for policy-bound consent; **current** mobile implements **preference** flows; **full Mvumo adaptive methods** = **Future phase** pending service readiness (documented in traceability matrix).

### 5.4 Critical flags / patient summary

Provider `EncounterScreen` / stores carry banner patterns; **explicit Mvumo DNR / no transfusion / proxy** flags require **contract** from BFF (`PatientSummary`, `CriticalFlags`) — **partial** until API fields guaranteed.

### 5.5 Voice dictation

- **Web**: `useSpeechToText` + `DictationButton` (`ui/one-ui-shell`).
- **Mobile**: **No silent recording** — `DictationAssistButton` in design system steers users to **OS keyboard dictation** and documents native STT module as optional follow-up (`@react-native-voice/voice` in dev client builds).

### 5.6 Telemedicine 7-stage lifecycle

Mapped in traceability matrix; **mobile** implements **list / join / end** for provider sessions and citizen **TelehealthListScreen**; triage/scheduling/pre-pack stages depend on **backend** stage APIs.

### 5.7 Enterprise / ERP selective

Inventory / finance / pharmacy hubs exist under provider tools and supervisor tabs — appropriate **selective** parity; full **Costa / Mushex consoles** remain **web/admin**.

## 6. Testing and CI

- Packages use **Vitest**; run `npm test` / `npm run type-check` per app from `apps/mobile` workspaces.
- **Android/iOS native builds** require local Expo EAS credentials — not executed in this audit run; document in CI when available.

## 7. Conclusion

Mobile architecture **already** followed a non-sidebar, Start-centric pattern. **Gaps** were mainly **wiring** (telehealth screen orphan, citizen support not in hub), **honesty** of offline/sync UI on citizen, and **documentation** of parity rules. **Mvumo full adaptive parity** and **shared RN↔web TypeScript contracts** remain **programme work** tied to backend maturity.

**Related artefacts**: `mobile-parity-traceability-matrix.md`, `mobile-navigation-and-parity-doctrine.md`, `mobile-security-privacy-notification-rules.md`, `mobile-offline-readiness-audit.md`, `mobile-no-fake-data-audit.md`.
