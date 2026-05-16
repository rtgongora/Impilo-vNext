# Service Duplication and Consolidation Register

Safe deduplication register for service, client, and component overlaps.
No destructive deletions are performed in this run.

Duplicated service display-name markers: **Product Registry**, **Wellness**.

| Possible Duplicate Area | Service A | Service B | Overlap | Canonical Owner Recommendation | Action Taken This Run | Remaining Action | Risk | Owner Decision Needed |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Registry Authority | product-registry-service | msika-service | Product and service catalog authority | MSIKA is canonical system of record | Alias marked non-authoritative; downstream pointers kept canonical-first | Retire alias runtime by approved sunset milestones | Low | No |
| Wellness Authority | wellness-service | simba-service | Wellness capability ownership and API namespace overlap | Simba is canonical SoR; wellness-service is compatibility alias | Added personal-data APIs to Simba runtime, tightened BFF routing to canonical Simba owner, expanded Simba contract parity, and replaced wellness mock surfacing in one-ui-shell | Continue alias retirement hygiene without changing canonical ownership | Medium | No |
| Public Health Tab Params Utility | ui/one-ui-shell `publicHealthTabParams.ts` | ui/experience `publicHealthTabParams.ts` | Duplicate route-tab parsing utility | Single shared utility module | Identified and documented; no risky cross-package move in this pass | Extract to shared package after import-path harmonization | Low | No |
| Query Hook Modules | ui/one-ui-shell `hooks/queries` | ui/experience `hooks/queries` | Multiple duplicate TanStack query wrappers over same `/internal/v1/**` APIs | Shared hook library per domain | Prioritized and cataloged for phased consolidation; no broad refactor in this pass | Consolidate in bounded slices after API client parity freeze | Medium | No |
| Shell App Catalog Definitions | one-ui-shell `lib/shell/app-registry.ts` | experience `lib/shell/app-registry.ts` | Parallel app/command definitions with partial divergence | One canonical app catalog baseline with app-specific extensions | Surfacing parity improved in one-ui-shell; divergence documented | Introduce shared core catalog + per-shell extension overlays | Medium | Yes |
