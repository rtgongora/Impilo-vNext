# Product Truth Recovery — Phase Report

> Generated: 2026-06-05T07:16:38.491Z
> Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
> Phase: **1 — Discovery & Documentation Only**

## 1. What was scanned

| Domain | Paths scanned |
|--------|---------------|
| Backend services | `services/*` (86 registry services, 653 controllers) |
| Experience BFF | `services/experience-bff` (216 controllers, 195 route prefixes) |
| API contracts | `contracts/openapi/` (95), `contracts/asyncapi/` (12) |
| Web experience | `ui/one-ui-shell/src/app/`, `routes.ts`, `hooks/queries/` |
| Mobile | `apps/mobile/citizen-app`, `apps/mobile/provider-app`, `apps/mobile/packages/` |
| Registry & doctrine | `docs/registry/`, `docs/doctrine/`, `docs/product/`, `CLAUDE.md`, `AGENTS.md` |
| Infrastructure | Dockerfiles (90), `deploy/helm/`, `.github/workflows/` |
| Canonical capabilities | `scripts/frontend/generate-parity-docs.mjs` (22 capabilities) |
| Database | Flyway migrations across 84 service modules |

## 2. Total components discovered

**2303** entries in the Product Truth Recovery Map.

## 3. Total backend capabilities

- **86** registry backend services
- **12** shared libraries
- **653** backend REST controllers
- **216** BFF controllers composing sovereign services

## 4. Total APIs/contracts

- **95** OpenAPI specifications
- **12** AsyncAPI event contracts
- **107** total API/event contracts

## 5. Total frontend routes

- **418** registered routes in `routes.ts`
- **27** on-disk pages not in registry (guard/sidebar gap)
- **163** TanStack Query hooks

## 6. Total mobile screens

- **146** mobile screens (citizen + provider apps)

## 7. Major hidden backend capability areas

Backend-rich domains with partial or missing experience surfacing (from canonical capability registry):

_None flagged as Not Wired/Fixture._

Additional signal: BFF implements ~195 route prefixes but `experience-bff.openapi.yaml` documents only a baseline subset — significant contract-runtime drift.

## 8. Major frontend/mobile visibility gaps

- TSHEPO: web=partial mobile=partial gap=Device block UX still admin-only
- VITO: web=partial mobile=partial gap=Issuance queue / card ops not fully surfaced
- VARAPI: web=partial mobile=partial gap=Council import / reconciliation queue thin
- TUSO: web=partial mobile=partial gap=Control-tower / digital readiness dashboards thin
- Indawo: web=partial mobile=partial gap=Map layer integration incomplete
- BUTANO: web=yes mobile=partial gap=Mobile conditions/allergies TODO
- Core Transaction: web=partial mobile=partial gap=Mobile journey shell shallow
- Public Health Ops: web=partial mobile=partial gap=Field ops mobile thinner than web
- Ndila: web=partial mobile=partial gap=Web ops map dashboards incomplete
- Nhume: web=partial mobile=partial gap=Dual path: nhume vs dispatch BFF
- Comms Hub: web=partial mobile=partial gap=Template/campaign admin depth
- Telemedicine: web=partial mobile=partial gap=RTC media intentionally blocked
- Msika / Msika Flow: web=partial mobile=partial gap=Order list routes 501 on some paths
- MusheX / COSTA: web=partial mobile=partial gap=No raw /mushex/v1 in browser
- Fundo: web=partial mobile=partial gap=Mobile learning shell shallow
- UBOMI: web=partial mobile=no gap=Mobile CRVS parity missing
- ZIBO: web=partial mobile=n/a gap=Separate zibo-web app only
- Nompilo: web=partial mobile=partial gap=Route context not always passed
- Integration Hub: web=partial mobile=partial gap=Adapter template admin thin
- Workflow / Dispatch: web=partial mobile=partial gap=Dispatch detail + offline queue UX
- Admin / Governance: web=partial mobile=partial gap=Keys/federation blocked



## 9. Unknown-needs-review items

**98** entries classified `unknown-needs-review`, primarily:
- Unregistered frontend pages (27)
- Query hooks without detected BFF paths
- Non-canonical UI workspaces

See [product-truth-recovery-map.json](../../reports/product/product-truth-recovery-map.json) filtered by `recommendedClassification=unknown-needs-review`.

## 10. Recommended next phase

**Phase 2 — Core Transaction Mapping**

Using this recovery map as input:
1. Map each canonical capability to `CoreTransactionType` and lifecycle stage per [CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md](../templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md)
2. Run `scripts/architecture/audit-core-transaction-compliance.py` and extend with actor-context matrix (citizen, provider, device, job, etc.)
3. Produce transaction spine document: actor → context → transaction → cooperating services → web/mobile journey → next action
4. Do **not** implement broad UI rewrites until transaction map is approved

## Artifacts

| Artifact | Path |
|----------|------|
| Recovery map (human) | [PRODUCT_TRUTH_RECOVERY_MAP.md](./PRODUCT_TRUTH_RECOVERY_MAP.md) |
| Recovery map (JSON) | [product-truth-recovery-map.json](../../reports/product/product-truth-recovery-map.json) |
| Recovery map (CSV) | [product-truth-recovery-map.csv](../../reports/product/product-truth-recovery-map.csv) |
| Rollups | [product-truth-rollups.md](../../reports/product/product-truth-rollups.md) |
| Generator | [generate-product-truth-recovery.mjs](../../scripts/product/generate-product-truth-recovery.mjs) |
