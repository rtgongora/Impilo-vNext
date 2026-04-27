# Mobile no-fake-data audit

**Date**: 2026-04-10  
**Scope**: `apps/mobile/citizen-app/src`, `apps/mobile/provider-app/src`, `apps/mobile/packages/*/src` (excluding `__tests__`, `*.test.ts`)

## 1. Method

- Ripgrep for: `mock`, `fake`, `sample`, `placeholder`, `hardcoded`, `demo`, `lorem`, `John Doe`, `TODO:`, `coming soon` (case-insensitive).
- Manual review of **success paths** that assert generic clinical outcomes without server payload (e.g. drug interaction “none found”).

## 2. Findings

| Area | Verdict | Notes |
|------|---------|------|
| **Test suites** | Allowed | `vi.mock`, `mockResolvedValue`, etc. confined to `__tests__` — acceptable per policy. |
| **TextInput placeholders** | Allowed | UI hints (“First name”, “YYYY-MM-DD”) — not clinical data. |
| **`specialtyWorkspaces.ts`** | Allowed | **Catalog labels** (e.g. “Voice Assessment”) are product copy, not patient-specific fiction. |
| **HomeScreen quick actions** | Allowed | Static **navigation** metadata (`QUICK_ACTIONS`) — routes only; no fabricated vitals. |
| **Provider dashboard metrics** | OK when API null | Uses `metrics?.patientsSeenToday ?? openEncounters.length` — second part is **real open encounters count**, not invented patients. |
| **SOAP / CDS demo panels** | Risk: UX copy | `ClinicalToolsScreen` SOAP save and CDS success use **Alert** confirmations; clinical **submission** must eventually bind to BFF/FHIR writers. Flagged as **integration debt**, not “fake patient” data. |
| **Citizen communities** | OK | Loads `/internal/v1/community/groups` — empty list is honest. |

## 3. Gaps / integration debt (not “fake data” but must not ship as if real)

1. **SOAP save** and similar alerts should be replaced by **persisted** encounter note APIs when wired.
2. **Drug interaction** success message should reflect **server-evaluated** response, not a static string, when backend returns structured result.
3. **Mvumo remote consent** UI on mobile should consume **mvumo-service + tshepo-consent-service** contracts—not static toggles alone.

## 4. Policy

- **Production** builds: `EXPO_PUBLIC_*` may point to staging servers but must not inject bundled JSON patients, consents, or bills.
- **Storybook / demos**: not present in repo today; if added, isolate under `apps/storybook-mobile` with fake data clearly non-production.

## 5. Sign-off

No evidence of bundled **fake patients**, **fake DNR flags**, **fake telemedicine sessions**, or **fake billing** in production `src` trees at audit time. Re-run this audit when adding new demo dashboards or seed imports to mobile.
