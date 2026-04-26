# Registry-driven templates — implementation report (wave 1)

## Summary

Introduced canonical **`registry-templates/`** (client, provider, facility, terminology) with machine-readable fields, value sets, ISO 3166-1 country data, and Zimbabwe admin **province** seeds (district/ward intentionally empty pending COD-AB import). Wired the **Experience** walk-in flow and **EHR stub** to **Vito-backed registration** via BFF, with extended metadata forwarded to Vito issuance and explicit **registry sync / delegation** flags on responses.

## Commands run

| Command | Result |
|---------|--------|
| `node scripts/registry/bootstrap-registry-templates.mjs` | OK — generates JSON under `registry-templates/`. |
| `curl` → `countries.iso3166-1.json` | OK — 249 ISO rows including ZW / ZWE / 716. |
| `cd ui/experience && npm run type-check` | OK |
| `cd ui/experience && npx vitest run src/lib/registry/iso3166.test.ts` | OK (2 tests) |
| `cd ui/ehr && npm run type-check` | OK |
| `cd services && mvn -DskipTests compile -pl experience-bff -am` | OK |

## Remaining gaps (next commits)

1. **Zimbabwe Admin 2–3**: Bulk-import COD-AB / ZIMSTAT into `terminology/data/*.seed.json` + Tuso API for read.
2. **Tuso Locality Gazetteer**: Replace proposal text field with live search + approval workflow API.
3. **Varapi / Tuso work-context UI**: Provider facility picker from Tuso; post-login resolution already partially in BFF.
4. **MusheX / COSTA**: Typed BFF routes for coverage eligibility + billing rule preview (currently payload only on register).
5. **Mobile apps**: Parity pass on `apps/mobile` (not modified in this wave).
6. **Vitest alias**: Add Vite resolve alias for `@registry-templates` if JSON-import tests are desired.
