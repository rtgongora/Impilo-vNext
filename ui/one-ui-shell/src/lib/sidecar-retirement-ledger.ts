/**
 * Sidecar Retirement Ledger — unified web experience orchestration layer
 *
 * Tracks consolidation of separate UI apps into the **single** actor-facing
 * Impilo web experience (doctrine §2.0: one orchestration surface, not competing
 * “shell” vs “experience” products).
 *
 * Ledger status strings like "absorbed into Experience" mean absorbed into that
 * unified layer. Sidecar folders remain as reference, not alternate entry points.
 */

export type SidecarRetirementStatus =
  | "absorbed into Experience"
  | "partially absorbed into Experience"
  | "retired — stub only"
  | "deprecated — do not use"
  | "retired — deleted 2026-07-12";

export interface SidecarRetirementEntry {
  sidecarApp: string;
  port: number;
  capability: string;
  experienceRoutes: string[];
  status: SidecarRetirementStatus;
  notes: string;
}

export const SIDECAR_RETIREMENT_LEDGER: SidecarRetirementEntry[] = [
  // ── Marketplace stack (4 apps → /marketplace/*) ───────────────────
  {
    sidecarApp: "ui/msika-flow-portal",
    port: 3010,
    capability: "Marketplace browse, cart, orders, pickup, substitutions",
    experienceRoutes: ["/marketplace", "/marketplace/catalog", "/marketplace/orders", "/marketplace/cart", "/marketplace/substitutions"],
    status: "retired — deleted 2026-07-12",
    notes: "Sidecar ui/msika-flow-portal deleted 2026-07-12 (Msika completion wave M8). BFF controllers: MarketplaceController, CommerceFlowController, CommerceSubstitutionController. Buyer lane now has real add-to-cart, priced cart, and a /marketplace/orders/[id]/pay money leg.",
  },
  {
    sidecarApp: "ui/msika-flow-vendor",
    port: 3011,
    capability: "Vendor fulfillment queue, order acceptance, ready/deliver",
    experienceRoutes: ["/marketplace/vendor", "/marketplace/vendor/orders"],
    status: "retired — deleted 2026-07-12",
    notes: "Sidecar ui/msika-flow-vendor deleted 2026-07-12 (Msika completion wave M8). BFF: VendorOperationsController + GET /commerce/vendor/me auto-binding; vendor pages resolve identity from the JWT actor.",
  },
  {
    sidecarApp: "ui/msika-flow-ops",
    port: 3012,
    capability: "Marketplace ops: stuck orders, vendor management, reviews",
    experienceRoutes: ["/marketplace/ops"],
    status: "retired — deleted 2026-07-12",
    notes: "Sidecar ui/msika-flow-ops deleted 2026-07-12 (Msika completion wave M8). BFF: MarketplaceOpsController — Vendors + Audit tabs and substitution reject are live in /marketplace/ops and /marketplace/substitutions.",
  },
  {
    sidecarApp: "ui/msika-web",
    port: 3013,
    capability: "Catalog governance: item CRUD, mappings, publish workflow",
    experienceRoutes: ["/finance/commerce-integrations"],
    status: "retired — deleted 2026-07-12",
    notes: "Sidecar ui/msika-web deleted 2026-07-12 (Msika completion wave M8). BFF: MsikaGovernanceController, ProductRegistryController — catalogue governance lives in /finance/msika-governance.",
  },

  // ── Finance stack (3 apps → /finance/*) ───────────────────────────
  {
    sidecarApp: "ui/mushex-finance-console",
    port: 3014,
    capability: "Ledger, settlements, reconciliation, refunds",
    experienceRoutes: ["/finance/settlements", "/finance/reconciliation", "/finance/refunds"],
    status: "absorbed into Experience",
    notes: "BFF controllers: SettlementController, ReconciliationController, RefundOpsController",
  },
  {
    sidecarApp: "ui/mushex-ops-console",
    port: 3015,
    capability: "Claims management, fraud detection, ops reviews",
    experienceRoutes: ["/finance/payer-ops", "/finance/commerce-integrations"],
    status: "absorbed into Experience",
    notes: "BFF controller: PayerOpsController, PayerClaimsController",
  },
  {
    sidecarApp: "ui/mushex-payer-portal",
    port: 3016,
    capability: "Payer payments, receipts, remittance",
    experienceRoutes: ["/finance/payments", "/finance/payer-ops"],
    status: "absorbed into Experience",
    notes: "BFF controller: FinanceLedgerController",
  },
  {
    sidecarApp: "ui/costa-console",
    port: 3017,
    capability: "Billing: approvals, audit, search, tariff config, simulation",
    experienceRoutes: ["/finance/billing", "/finance/tariffs", "/finance/claims"],
    status: "absorbed into Experience",
    notes: "BFF controller: FinanceController (CostaServiceClient)",
  },

  // ── Clinical / SHR stack (3 apps → /ehr/*, /lab/*) ────────────────
  {
    sidecarApp: "ui/butano-web",
    port: 3018,
    capability: "IPS viewer, timeline stats, reconciliation, triggers",
    experienceRoutes: ["/ehr/[patientId]/ips", "/ehr/[patientId]/timeline", "/operations/butano"],
    status: "absorbed into Experience",
    notes: "BFF controller: SummaryProxyController (ButanoServiceClient)",
  },
  {
    sidecarApp: "ui/oros-web",
    port: 3019,
    capability: "Lab worklists, orders, results, catalog, reconciliation",
    experienceRoutes: ["/lab", "/lab/worklist", "/lab/results", "/lab/catalog", "/lab/reconciliation"],
    status: "absorbed into Experience",
    notes: "BFF controller: LabOrdersController (OrosServiceClient). New /lab zone.",
  },
  {
    sidecarApp: "ui/ehr",
    port: 3002,
    capability: "EHR stub (single page)",
    experienceRoutes: ["/ehr/[patientId]"],
    status: "retired — stub only",
    notes: "Full EHR with 30+ pages exists in experience /ehr/*. Sidecar was a minimal stub.",
  },

  // ── Operations stack (2 apps → /operations/*) ─────────────────────
  {
    sidecarApp: "ui/ops-console",
    port: 3001,
    capability: "VITO ops: dedup, issuance, match queue, card mgmt, config",
    experienceRoutes: ["/operations", "/operations/vito"],
    status: "absorbed into Experience",
    notes: "New /operations zone with VITO/BUTANO/asset/equipment sub-pages.",
  },
  {
    sidecarApp: "ui/inventory-web",
    port: 3011,
    capability: "Inventory dashboard, stock counts, movements, requisitions",
    experienceRoutes: ["/inventory", "/inventory/movements", "/inventory/counts", "/inventory/requisitions"],
    status: "absorbed into Experience",
    notes: "BFF controller: InventoryController. Already fully in experience since Wave 8.",
  },

  // ── Identity / citizen stack (3 apps → /citizen/*, /share/*, /home/*) ─
  {
    sidecarApp: "ui/portal",
    port: 3003,
    capability: "Citizen health ID: QR, request, recovery, delegated pickup",
    experienceRoutes: ["/citizen", "/citizen/health-id/qr", "/citizen/health-id/request", "/citizen/id-recovery", "/citizen/delegated-pickup"],
    status: "absorbed into Experience",
    notes: "citizenPortalClient.ts proxies to VITO via Next.js rewrites.",
  },
  {
    sidecarApp: "ui/self-service",
    port: 3004,
    capability: "Credential claim, my credentials, my documents, verification",
    experienceRoutes: ["/home/credentials", "/home/documents", "/verify/credential", "/share/claim"],
    status: "absorbed into Experience",
    notes: "credentialVerifyPublic.ts + shareSlipPublic.ts for public verify/claim.",
  },
  {
    sidecarApp: "ui/ops-docs",
    port: 3021,
    capability: "Document operations: issue, verify, print jobs, share links",
    experienceRoutes: ["/id-services", "/home/documents"],
    status: "absorbed into Experience",
    notes: "BFF controller: IdentityServicesController + document store proxy.",
  },

  // ── Specialized consoles (3 apps → various zones) ─────────────────
  {
    sidecarApp: "ui/support-console",
    port: 3005,
    capability: "Ticket management, messaging, knowledge base, incidents",
    experienceRoutes: ["/support", "/support/tickets", "/support/knowledge-base", "/communication"],
    status: "absorbed into Experience",
    notes: "New /support zone. Messaging merged with /communication.",
  },
  {
    sidecarApp: "ui/developer-console",
    port: 3006,
    capability: "API catalog, client registration, certification, sandbox",
    experienceRoutes: ["/developer", "/developer/api-catalog", "/developer/clients", "/developer/sandbox"],
    status: "absorbed into Experience",
    notes: "New /developer zone. Health OS §10: governed app extensibility.",
  },
  {
    sidecarApp: "ui/zibo-web",
    port: 3007,
    capability: "Terminology: CodeSystems, ValueSets, ConceptMaps, FHIR artifacts",
    experienceRoutes: ["/registry/terminology", "/registry/terminology/[id]"],
    status: "absorbed into Experience",
    notes: "Terminology browser already exists in experience registry zone.",
  },

  // ── Service-specific thin UIs ─────────────────────────────────────
  {
    sidecarApp: "ui/pharmacy-web",
    port: 3008,
    capability: "Pharmacy dispensing, reconciliation, worklists",
    experienceRoutes: ["/pharmacy", "/pharmacy/dispense", "/pharmacy/stock", "/pharmacy/prescriptions"],
    status: "absorbed into Experience",
    notes: "BFF controller: PharmacyController. Fully in experience since Wave 6.",
  },
  {
    sidecarApp: "ui/pct-web",
    port: 3009,
    capability: "Patient care tracker: control tower, queues, sorting",
    experienceRoutes: ["/queue", "/clinical/control-tower"],
    status: "absorbed into Experience",
    notes: "BFF controller: QueueController, TriageController. In experience since Wave 6.",
  },

  // ── Shell / infrastructure ────────────────────────────────────────
  {
    sidecarApp: "ui/experience",
    port: 3099,
    capability: "Former standalone Next workspace (same route tree; merged into one-ui-shell)",
    experienceRoutes: [],
    status: "deprecated — do not use",
    notes: "Do not use as a product entry. Run **ui/one-ui-shell** on port **3000** (Docker/runtime). This folder is legacy-only.",
  },
];

/** Summary statistics */
export const SIDECAR_STATS = {
  totalSidecars: SIDECAR_RETIREMENT_LEDGER.length,
  absorbed: SIDECAR_RETIREMENT_LEDGER.filter((e) => e.status === "absorbed into Experience").length,
  retired: SIDECAR_RETIREMENT_LEDGER.filter((e) => e.status === "retired — stub only").length,
  deprecated: SIDECAR_RETIREMENT_LEDGER.filter((e) => e.status === "deprecated — do not use").length,
  partial: SIDECAR_RETIREMENT_LEDGER.filter((e) => e.status === "partially absorbed into Experience").length,
};
