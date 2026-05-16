# Remediation Summary

Date: 2026-05-16  
Scope: Implementation integrity remediation (web + mobile honesty/wiring baseline)

## What Was Remediated

1. **Fixture honesty for doctrine pages (web)**  
   Added explicit fixture maturity labels and warning copy to:
   - `ui/one-ui-shell/src/app/core-transaction/page.tsx`
   - `ui/one-ui-shell/src/app/client-journey/page.tsx`
   - `ui/one-ui-shell/src/app/provider-workspace/page.tsx`
   - `ui/one-ui-shell/src/app/platform-journey/page.tsx`

2. **Route registry integrity (web)**  
   Added `/home/referrals` to route registry and updated route count:
   - `ui/one-ui-shell/src/lib/routes.ts`

3. **Reality label system (web + mobile)**  
   - Web: `ui/one-ui-shell/src/components/FeatureMaturityBadge.tsx`
   - Mobile: `apps/mobile/packages/mobile-design-system/src/components/FeatureMaturityBadge.tsx`
   - Exported mobile component via `apps/mobile/packages/mobile-design-system/src/index.ts`

4. **Mobile placeholder honesty (citizen app)**  
   Added explicit not-wired labels and safer copy/state in:
   - `apps/mobile/citizen-app/src/screens/personal/ConditionsSection.tsx`
   - `apps/mobile/citizen-app/src/screens/personal/AllergiesSection.tsx`
   - `apps/mobile/citizen-app/src/screens/discover/ProviderDiscoveryScreen.tsx`

5. **Integrity scanning utility**
   - Added `scripts/audit-ui-integrity.ts` and runnable `scripts/audit-ui-integrity.mjs` to scan for common UI integrity anti-patterns.

## Validation Commands and Results

Executed:

- `npm run lint -- --format json --output-file lint-report.json` (inside `ui/one-ui-shell`)  
  Result: **No ESLint warnings or errors**.

- `npm run test` (inside `ui/one-ui-shell`)  
  Result: **157/157 files passed, 496/496 tests passed**.

- Targeted test after imaging icon regression fix:  
  `npx vitest run src/app/ehr/[patientId]/imaging/page.test.tsx`  
  Result: **Passed**.

- Integrity scan utility:  
  `node scripts/audit-ui-integrity.mjs`  
  Result: generated `docs/audits/ui-integrity-scan-report.json`.

- `npm run test -- --run` (inside `apps/mobile/citizen-app`)  
  Result: **17/17 files passed, 92/92 tests passed**.

- `npm run test -- --run` (inside `apps/mobile/provider-app`)  
  Result: **failed due environment/module resolution issue** (`@expo/vector-icons ... createIconSet` missing in test runtime), not introduced by this remediation change set.

## Outstanding Backlog (Not Completed In This Cycle)

| ID | Priority | Item |
|---|---|---|
| BL-001 | P0 | Replace doctrine fixtures with live core-transaction API hooks and canonical contract models. |
| BL-002 | P0 | Resolve provider mobile messaging endpoint family mismatch and add parity tests. |
| BL-003 | P1 | Surface workflow and dispatch backend capabilities in web/mobile operator UX. |
| BL-004 | P1 | Unify identity/core enum contracts between shared-ui and canonical contract sources. |
| BL-005 | P1 | Wire citizen conditions/allergies/provider discovery screens to backend APIs. |
| BL-006 | P2 | Converge web/mobile Nompilo capability matrix and add explicit unsupported-state UX. |

## Documentation Pack Produced

This remediation run added/updated all required audit artifacts under `docs/audits` for implementation integrity, parity, wiring, and backlog traceability.
