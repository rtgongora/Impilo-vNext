#!/usr/bin/env node
/**
 * Regenerates parity sweep documentation from embedded capability registry.
 * Run from Impilo-vNext root: node scripts/frontend/generate-parity-docs.mjs
 */

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const OUT = join(ROOT, "docs/frontend");

const CAPABILITIES = [
  {
    plane: "Trust",
    domain: "TSHEPO",
    capability: "Trust admin (policies, break-glass, devices, audit)",
    backend: "/internal/v1/admin/trust/*",
    contract: "contracts/openapi/tshepo-authz.openapi.yaml",
    webRoute: "/admin/trust, /settings/security",
    webClient: "useTrustAdmin.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Device block UX still admin-only",
    action: "Trust governance strip on settings; deepen device admin",
  },
  {
    plane: "Registry",
    domain: "VITO",
    capability: "Client search, register, profile, Health ID",
    backend: "/internal/v1/identity/*, /internal/v1/registry/*",
    contract: "contracts/openapi/vito.openapi.yaml",
    webRoute: "/id-services, /registry/*",
    webClient: "useIdentity.ts, useClientRegistry.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Issuance queue / card ops not fully surfaced",
    action: "Registry hub depth + mobile health-id parity",
  },
  {
    plane: "Registry",
    domain: "VARAPI",
    capability: "Provider registry, licenses, privileges, CPD",
    backend: "/internal/v1/registry/*",
    contract: "contracts/openapi/varapi.openapi.yaml",
    webRoute: "/registry/providers/*",
    webClient: "useRegistry.ts, useLicenses.ts, useCpd.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "no",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Council import / reconciliation queue thin",
    action: "Verification workflow screens",
  },
  {
    plane: "Registry",
    domain: "TUSO",
    capability: "Facility/workspace registry, bookings",
    backend: "/internal/v1/facilities, /internal/v1/registry/*",
    contract: "contracts/openapi/tuso.openapi.yaml",
    webRoute: "/facility/*, /registry/facilities/*",
    webClient: "useFacilities.ts, useTusoRegistry.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Control-tower / digital readiness dashboards thin",
    action: "Facility operating model detail pages",
  },
  {
    plane: "Registry",
    domain: "Indawo",
    capability: "Public health site registry",
    backend: "/internal/v1/public-health/site-registry/*",
    contract: "contracts/openapi/indawo.openapi.yaml",
    webRoute: "/public-health/*",
    webClient: "usePublicHealth.ts, useSiteRegistry.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "no",
    maturity: "Partial",
    priority: "MEDIUM",
    gap: "Map layer integration incomplete",
    action: "Ndila map panel on site registry",
  },
  {
    plane: "Clinical",
    domain: "BUTANO",
    capability: "SHR summary, timeline, allergies, conditions",
    backend: "/internal/v1/summary/*, /internal/v1/timeline",
    contract: "contracts/openapi/butano.custom.openapi.yaml",
    webRoute: "/ehr/[patientId]/*",
    webClient: "useSummary.ts, useTimeline.ts",
    web: "yes",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Live",
    priority: "MEDIUM",
    gap: "Mobile conditions/allergies TODO",
    action: "Wire citizen personal sections to BFF",
  },
  {
    plane: "Clinical",
    domain: "Core Transaction",
    capability: "Transaction composition, journey steppers",
    backend: "/internal/v1/core-transactions/*",
    contract: "contracts/core-transaction.ts",
    webRoute: "/core-transaction",
    webClient: "useCoreTransactionExperience.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Mobile journey shell shallow",
    action: "Deepen command/handoff wiring",
  },
  {
    plane: "Data & Intelligence",
    domain: "Public Health Ops",
    capability: "Inspections, outbreaks, campaigns, intelligence",
    backend: "/internal/v1/public-health/*",
    contract: "contracts/openapi/surveillance.openapi.yaml",
    webRoute: "/public-health/*",
    webClient: "usePublicHealth.ts, useSurveillance.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Field ops mobile thinner than web",
    action: "Provider field tasks parity",
  },
  {
    plane: "Integration & Edge",
    domain: "Ndila",
    capability: "Geocode, routes, intelligence layers",
    backend: "/api/v1/ndila/*",
    contract: "contracts/openapi/ndila.openapi.yaml",
    webRoute: "NdilaIntelligencePanel",
    webClient: "lib/ndila/ndila-client.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "no",
    maturity: "Partial",
    priority: "MEDIUM",
    gap: "Web ops map dashboards incomplete",
    action: "Reusable map component rollout",
  },
  {
    plane: "Enterprise",
    domain: "Nhume",
    capability: "Dispatch, delivery, fleet tracking",
    backend: "/api/v1/nhume/*, /internal/v1/mobile/*/nhume/*",
    contract: "nhume-service controllers",
    webRoute: "/nhume/*, /operations/dispatch",
    webClient: "lib/nhume.ts, useDispatchOps.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Dual path: nhume vs dispatch BFF",
    action: "Unified operator UX + maturity labels",
  },
  {
    plane: "Experience",
    domain: "Comms Hub",
    capability: "Omnichannel, messaging, notifications",
    backend: "/internal/v1/omnichannel/*, /internal/v1/communication/*",
    contract: "contracts/openapi/channels.openapi.yaml",
    webRoute: "/communication, /omnichannel",
    webClient: "useOmnichannel.ts, useCommunication.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "MEDIUM",
    gap: "Template/campaign admin depth",
    action: "Comms dashboard actionable tasks",
  },
  {
    plane: "Clinical",
    domain: "Telemedicine",
    capability: "Teleconsult sessions, scheduling",
    backend: "/internal/v1/teleconsult/*",
    contract: "experience-bff.openapi.yaml",
    webRoute: "/telemedicine/*",
    webClient: "useTelemedicine.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "RTC media intentionally blocked",
    action: "Label Blocked for RTC; live scheduling/records",
  },
  {
    plane: "Enterprise",
    domain: "Msika / Msika Flow",
    capability: "Catalog, orders, marketplace",
    backend: "/internal/v1/marketplace/*, /internal/v1/commerce/*",
    contract: "contracts/openapi/msika-flow.openapi.yaml",
    webRoute: "/marketplace/*",
    webClient: "useMarketplace.ts, useCommerceFlow.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "no",
    maturity: "Partial",
    priority: "MEDIUM",
    gap: "Order list routes 501 on some paths",
    action: "Honest blocked states on list routes",
  },
  {
    plane: "Enterprise",
    domain: "MusheX / COSTA",
    capability: "Payments, claims, billing, tariffs",
    backend: "/internal/v1/finance/*, /internal/v1/wallet/*",
    contract: "contracts/openapi/mushex.openapi.yaml, costa.openapi.yaml",
    webRoute: "/finance/*, /wallet",
    webClient: "useMusheWallet.ts, useFinanceBillingWorkspace.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "no",
    maturity: "Partial",
    priority: "HIGH",
    gap: "No raw /mushex/v1 in browser",
    action: "Finance journey mobile parity",
  },
  {
    plane: "Experience",
    domain: "Fundo",
    capability: "LMS courses, studio, certificates",
    backend: "/internal/v1/learning/v11/*",
    contract: "contracts/openapi/learning.openapi.yaml",
    webRoute: "/learning/*",
    webClient: "useFundoLms.ts, useFundoStudio.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "MEDIUM",
    gap: "Mobile learning shell shallow",
    action: "Fundo mobile module depth",
  },
  {
    plane: "Experience",
    domain: "Social",
    capability: "Timeline, communities, pages",
    backend: "/internal/v1/social/*",
    contract: "contracts/openapi/social.openapi.yaml",
    webRoute: "/social, /communities, /pages",
    webClient: "useSocial.ts",
    web: "yes",
    mobile: "yes",
    nompilo: "no",
    maturity: "Live",
    priority: "LOW",
    gap: "Moderation admin partial",
    action: "Moderation workflow surfacing",
  },
  {
    plane: "Registry",
    domain: "UBOMI",
    capability: "CRVS births/deaths",
    backend: "ubomi-service /v1/births",
    contract: "contracts/openapi/ubomi.openapi.yaml",
    webRoute: "/ubomi",
    webClient: "useUbomiRegistry.ts (new)",
    web: "partial",
    mobile: "no",
    nompilo: "no",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Mobile CRVS parity missing",
    action: "UBOMI births/deaths/verify live when service up; mobile parity",
  },
  {
    plane: "Registry",
    domain: "ZIBO",
    capability: "Terminology governance",
    backend: "/v1/artifacts, /v1/packs",
    contract: "contracts/openapi/zibo.openapi.yaml",
    webRoute: "ui/zibo-web",
    webClient: "ziboApi.ts",
    web: "partial",
    mobile: "n/a",
    nompilo: "no",
    maturity: "Partial",
    priority: "LOW",
    gap: "Separate zibo-web app only",
    action: "Shell link + maturity on terminology nav",
  },
  {
    plane: "Experience",
    domain: "Nompilo",
    capability: "Guidance, LLM chat, core-transaction assist",
    backend: "/internal/v1/guidance/*, /internal/v1/llm/*",
    contract: "experience-bff.openapi.yaml",
    webRoute: "/ask, global command bar",
    webClient: "useGuidance.ts, NompiloGlobalCommandBar",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Route context not always passed",
    action: "Context query params + fallback label",
  },
  {
    plane: "Integration",
    domain: "Integration Hub",
    capability: "Routes, dead letters, dispatch",
    backend: "/internal/v1/integration-hub/*",
    contract: "contracts/openapi/integration-hub.openapi.yaml",
    webRoute: "/admin/integration-status, /settings/integrations",
    webClient: "useIntegrationHub.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "no",
    maturity: "Partial",
    priority: "MEDIUM",
    gap: "Adapter template admin thin",
    action: "Integration admin depth",
  },
  {
    plane: "Platform",
    domain: "Workflow / Dispatch",
    capability: "Workflow definitions, instances, dispatch tasks",
    backend: "/internal/v1/workflows/*, /internal/v1/dispatch/*",
    contract: "contracts/openapi/workflow.openapi.yaml",
    webRoute: "/operations/workflows, /operations/dispatch",
    webClient: "useDispatchOps.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "partial",
    maturity: "Partial",
    priority: "HIGH",
    gap: "Dispatch detail + offline queue UX",
    action: "Workflow instance table + dispatch guided detail",
  },
  {
    plane: "Platform",
    domain: "Admin / Governance",
    capability: "Users, tenants, roles, audit, feature flags",
    backend: "/internal/v1/admin/*",
    contract: "experience-bff.openapi.yaml",
    webRoute: "/admin/*, /organization-admin/*",
    webClient: "useAdminUsers.ts, useTrustAdmin.ts",
    web: "partial",
    mobile: "partial",
    nompilo: "no",
    maturity: "Partial",
    priority: "MEDIUM",
    gap: "Keys/federation blocked",
    action: "Document Blocked surfaces explicitly",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Donor engagement (register, profile, eligibility, feedback)",
    backend: "/internal/v1/madi/donors/*, /internal/v1/mobile/citizen/madi/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/donor/*",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "Live",
    nompilo: "Live",
    maturity: "Live",
    priority: "HIGH",
    gap: "—",
    action: "Guided pre-screening + Nompilo assist on web and citizen mobile",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Donation drive scheduling and field capture",
    backend: "/internal/v1/madi/drives/*, /internal/v1/mobile/provider/madi/drives/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/drives/*",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "Live",
    nompilo: "no",
    maturity: "Live",
    priority: "HIGH",
    gap: "—",
    action: "Offline queue + sync-conflict resolution on provider mobile",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Blood processing and component labelling",
    backend: "/internal/v1/madi/processing/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/processing",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "no",
    nompilo: "no",
    maturity: "Live",
    priority: "MEDIUM",
    gap: "—",
    action: "ZIBO SNOMED deep-links on /madi/processing component selector",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Blood bank stock and inventory balance",
    backend: "/internal/v1/madi/blood-banks/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/blood-bank/*",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "no",
    nompilo: "no",
    maturity: "Live",
    priority: "HIGH",
    gap: "—",
    action: "IoT fridge monitoring at /madi/blood-bank/fridges",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Clinical blood order (crossmatch, reserve, issue)",
    backend: "/internal/v1/madi/orders/*, /internal/v1/mobile/provider/madi/orders/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/orders/*",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "Live",
    nompilo: "partial",
    maturity: "Live",
    priority: "HIGH",
    gap: "—",
    action: "OROS lab worklist deep-link on order detail",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Transfusion episode and observation capture",
    backend: "/internal/v1/madi/transfusions/*, /internal/v1/mobile/provider/madi/transfusions/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/transfusion/*",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "Live",
    nompilo: "partial",
    maturity: "Live",
    priority: "HIGH",
    gap: "—",
    action: "VITO biometric + barcode bedside verify on web and mobile",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Haemovigilance (adverse reaction reporting)",
    backend: "/internal/v1/madi/haemovigilance/*, /internal/v1/mobile/provider/madi/haemovigilance/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/haemovigilance",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "Live",
    nompilo: "no",
    maturity: "Live",
    priority: "HIGH",
    gap: "—",
    action: "National roll-up at /madi/haemovigilance/national",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "Central blood bank coordination",
    backend: "/internal/v1/madi/central-bank/*",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/central-bank",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "no",
    nompilo: "no",
    maturity: "Live",
    priority: "MEDIUM",
    gap: "—",
    action: "Emergency redistribution request + approve on /madi/central-bank",
  },
  {
    plane: "Clinical",
    domain: "MADI",
    capability: "MADI dashboards and programme KPIs",
    backend: "/internal/v1/madi/dashboard",
    contract: "contracts/openapi/madi.openapi.yaml",
    webRoute: "/madi/dashboard",
    webClient: "useMadi.ts",
    web: "Live",
    mobile: "no",
    nompilo: "partial",
    maturity: "Live",
    priority: "MEDIUM",
    gap: "—",
    action: "30-day forecast table on /madi/dashboard from order + stock signals",
  },
];

function mdTable(rows, columns) {
  const header = `| ${columns.join(" | ")} |`;
  const sep = `| ${columns.map(() => "---").join(" | ")} |`;
  const body = rows
    .map((r) => `| ${columns.map((c) => String(r[c] ?? "").replace(/\|/g, "\\|")).join(" | ")} |`)
    .join("\n");
  return `${header}\n${sep}\n${body}`;
}

function writeMatrix() {
  const cols = [
    "plane",
    "domain",
    "capability",
    "backend",
    "contract",
    "webRoute",
    "webClient",
    "web",
    "mobile",
    "nompilo",
    "maturity",
    "priority",
    "gap",
    "action",
  ];
  const content = `# Backend Capability to Frontend Surfacing Matrix

> Generated: ${new Date().toISOString().slice(0, 10)}. Regenerate: \`node scripts/frontend/generate-parity-docs.mjs\`

## Summary

| Maturity | Count |
|----------|-------|
${["Live", "Partial", "Fixture", "Not Wired", "Blocked"]
  .map((m) => {
    const n = CAPABILITIES.filter((c) => c.maturity === m).length;
    return `| ${m} | ${n} |`;
  })
  .join("\n")}

## Matrix

${mdTable(CAPABILITIES, cols)}

## Trust headers (all BFF paths)

Mandatory where applicable: \`X-Tenant-Id\`, \`X-Correlation-Id\`, \`X-Device-Fingerprint\`, \`X-Purpose-Of-Use\`, \`X-Actor-Id\`, \`X-Actor-Type\`, \`X-Facility-Id\`, \`X-Workspace-Id\`, \`X-Shift-Id\`.

Injected by \`ui/one-ui-shell/src/lib/api-client.ts\` and \`apps/mobile/packages/mobile-trust\`.

## Intentional non-BFF exceptions

| Client | Path | Reason |
|--------|------|--------|
| Nhume web | \`/api/v1/nhume/*\` | Legacy logistics gateway |
| Ndila mobile | \`/api/v1/ndila/*\` | Geospatial SDK |
| ZIBO admin | \`/v1/*\` via zibo-web | Sovereign terminology console |
`;
  writeFileSync(join(OUT, "BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md"), content);
}

function writeImplementationStatus() {
  const content = `# Frontend Implementation Status

> Updated: ${new Date().toISOString().slice(0, 10)}

## Route registries

| Surface | Registry | Count |
|---------|----------|-------|
| Web canonical | \`ui/one-ui-shell/src/lib/routes.ts\` | 370 |
| Web continuity | \`ui/one-ui-shell/src/lib/routes.ts\` | 258 |
| Citizen mobile | Tab + Personal section router | 7 tabs, 30+ sections |
| Provider mobile | Mode router + ClinicalTools | 5 modes, 30+ tools |

## Live functionality (representative)

- Queue/triage/worklist (web + mobile)
- EHR summary/timeline (web BFF)
- Social timeline/communities (web + mobile)
- Core transaction feed (web BFF â€” no fixture injection)
- Marketplace launcher (web + mobile BFF)
- Public health reads + fail-close writes (web)
- Teleconsult lifecycle (web; RTC blocked)

## Partial functionality

- Trust admin, registry identity ops, finance/commerce, learning, Nompilo, workflow/dispatch ops, Ndila intelligence, UBOMI

## Fixture / demo (must be labelled)

- Legacy journey doctrine scaffolding where explicitly badged
- Nompilo mobile fallback copy when LLM unavailable

## Not wired

- UBOMI civil registry workflows (page is honest placeholder)
- Some marketplace list routes (501 from BFF)
- Admin keys, federation registry (blocked pending contract)

## Blocked

- Teleconsult RTC/media (intentional)
- Raw MusheX \`/mushex/v1\` browser pass-through

## Mobile parity status

See [WEB_MOBILE_SURFACING_PARITY.md](./WEB_MOBILE_SURFACING_PARITY.md).

## Nompilo grounding

- Global command bar + \`/ask\` route
- Journey-aware suggestions via \`classifyRouteJourney\`
- Context query: \`?from=\` pathname on Ask navigation

## Build / test (run per workspace)

\`\`\`bash
cd ui && npm run type-check
cd ui/one-ui-shell && npm run test && npm run build
cd apps/mobile && pnpm test
\`\`\`
`;
  writeFileSync(join(OUT, "FRONTEND_IMPLEMENTATION_STATUS.md"), content);
}

export { CAPABILITIES };

function isMain() {
  const entry = process.argv[1] ?? "";
  return entry.includes("generate-parity-docs.mjs");
}

if (isMain()) {
  mkdirSync(OUT, { recursive: true });
  writeMatrix();
  writeImplementationStatus();
  console.log("Wrote docs/frontend matrix and implementation status");
}
