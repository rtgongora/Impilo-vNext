export type SidecarRetirementStatus =
  | "absorbed into Experience"
  | "partially absorbed into Experience"
  | "retired sidecar path"
  | "blocked by missing backend contract";

export interface SidecarRetirementEntry {
  sidecarApp: string;
  capability: string;
  oldUiPath: string;
  newExperiencePath: string;
  status: SidecarRetirementStatus;
  notes: string;
  blockerContract?: string;
}

export const SIDECAR_RETIREMENT_LEDGER: SidecarRetirementEntry[] = [
  {
    sidecarApp: "ui/knowledge-admin",
    capability: "National clinical knowledge curation queue",
    oldUiPath: "ui/knowledge-admin:/",
    newExperiencePath: "/admin/clinical-curation",
    status: "absorbed into Experience",
    notes:
      "ClinicalCurationController-backed review and decision workflows now run inside the Experience admin plane with the same BFF contract.",
  },
  {
    sidecarApp: "ui/msika-flow-portal",
    capability: "Facility marketplace shell",
    oldUiPath: "ui/msika-flow-portal:/browse,/cart,/orders,/pickup,/substitutions",
    newExperiencePath:
      "/marketplace, /marketplace/catalog, /marketplace/orders/[id], /marketplace/cart, /marketplace/pickup, /marketplace/substitutions",
    status: "absorbed into Experience",
    notes:
      "CommerceFlowController, ProductRegistryController, and CommerceSubstitutionController now back the operator path in Experience.",
  },
  {
    sidecarApp: "ui/msika-flow-vendor",
    capability: "Vendor queue and fulfillment",
    oldUiPath: "ui/msika-flow-vendor:/queue,/orders,/fulfillment",
    newExperiencePath: "/marketplace/vendor, /marketplace/vendor/orders",
    status: "absorbed into Experience",
    notes:
      "VendorOperationsController is live. Experience still uses trusted operator roles rather than a dedicated vendor actor plane.",
  },
  {
    sidecarApp: "ui/msika-flow-ops",
    capability: "Marketplace ops reviews, stuck orders, and vendor governance",
    oldUiPath: "ui/msika-flow-ops:/reviews,/stuck,/audit,/vendors",
    newExperiencePath: "/marketplace/ops",
    status: "absorbed into Experience",
    notes: "MarketplaceOpsController-backed workflows now run in Experience.",
  },
  {
    sidecarApp: "ui/msika-web",
    capability: "Catalog governance, mappings, publish, and import",
    oldUiPath: "ui/msika-web:/catalogs,/mappings,/publish,/import",
    newExperiencePath: "/finance/msika-governance",
    status: "absorbed into Experience",
    notes:
      "Catalog governance, mapping review, publishing, and CSV import now run in Experience through the canonical MSIKA governance workspace.",
  },
  {
    sidecarApp: "ui/mushex-finance-console",
    capability: "Ledger, settlements, reconciliation, and refunds",
    oldUiPath: "ui/mushex-finance-console:/ledger,/settlements,/reconciliation,/refunds",
    newExperiencePath: "/finance/ledger, /finance/settlements, /finance/reconciliation, /finance/refunds",
    status: "absorbed into Experience",
    notes:
      "FinanceLedgerController, SettlementController, ReconciliationController, and RefundOpsController are all consumed in Experience.",
  },
  {
    sidecarApp: "ui/mushex-ops-console",
    capability: "Fraud, adapters, ops reviews, and claims operations",
    oldUiPath: "ui/mushex-ops-console:/adapters,/fraud,/reviews,/claims",
    newExperiencePath: "/finance/payer-ops, /finance/payer-claims, /finance/payer-claims/[claimId]",
    status: "absorbed into Experience",
    notes:
      "Adapters, fraud, ops reviews, payer-claim queue, and payer-claim detail actions now run in Experience through typed finance routes.",
  },
  {
    sidecarApp: "ui/mushex-payer-portal",
    capability: "Payments, receipts, and remittance actions",
    oldUiPath: "ui/mushex-payer-portal:/payments,/receipts,/remittance",
    newExperiencePath: "/finance/payments, /finance/payer-ops",
    status: "absorbed into Experience",
    notes:
      "Experience now handles payment-intent, receipts, remittance, and refund-adjacent flows through typed finance routes.",
  },
  {
    sidecarApp: "ui/costa-console",
    capability: "Billing, claims, tariffs, and finance simulation",
    oldUiPath: "ui/costa-console:/billing,/claims,/tariffs",
    newExperiencePath: "/finance/billing, /finance/claims, /finance/tariffs",
    status: "absorbed into Experience",
    notes: "Costa-backed finance workflows remain in the Experience shell.",
  },
  {
    sidecarApp: "ui/butano-web",
    capability: "International Patient Summary and longitudinal patient context",
    oldUiPath: "ui/butano-web:/ips,/timeline,/triggers",
    newExperiencePath: "/ehr/[patientId]/summary, /ehr/[patientId]/ips, /ehr/[patientId]/timeline",
    status: "absorbed into Experience",
    notes: "IPS retrieval and drill-down are now real in the chart workflow through SummaryProxyController.",
  },
  {
    sidecarApp: "ui/ehr",
    capability: "Legacy single-page EHR stub",
    oldUiPath: "ui/ehr:/",
    newExperiencePath: "/ehr/[patientId]",
    status: "retired sidecar path",
    notes: "The legacy EHR stub is superseded by the full Experience EHR route family.",
  },
  {
    sidecarApp: "ui/portal",
    capability: "Citizen health ID, recovery, and delegated pickup",
    oldUiPath: "ui/portal:/my-qr,/request-id,/recovery,/pickup",
    newExperiencePath:
      "/citizen, /citizen/health-id/qr, /citizen/health-id/request, /citizen/id-recovery, /citizen/delegated-pickup",
    status: "absorbed into Experience",
    notes: "Citizen services are now canonically discoverable in Experience routes and sidebar life-context navigation.",
  },
  {
    sidecarApp: "ui/self-service",
    capability: "Credential verification, shared-document claim, personal documents, and personal credentials",
    oldUiPath: "ui/self-service:/verify,/claim,/my-documents,/my-credentials",
    newExperiencePath: "/verify/credential, /share/claim, /home/documents, /home/credentials",
    status: "absorbed into Experience",
    notes:
      "Verify, share-claim, personal documents, and the personal credential vault now run in Experience through the public verifier, share-slip, clinical-tools, and credential-vault bridges.",
  },
  {
    sidecarApp: "ui/ops-docs",
    capability: "Document operations and issue/verify tooling",
    oldUiPath: "ui/ops-docs:/issue,/verify,/print-jobs,/share",
    newExperiencePath: "/id-services, /verify/credential, /share/claim",
    status: "partially absorbed into Experience",
    notes: "Public verification and share claim are now in Experience, but document-ops parity is not complete.",
    blockerContract: "Document issue, download, and print-job workflows still need verified Experience contracts.",
  },
  {
    sidecarApp: "ui/oros-web",
    capability: "Laboratory worklists, orders, results, and catalog",
    oldUiPath: "ui/oros-web:/worklist,/orders,/results,/catalog,/reconciliation",
    newExperiencePath: "/lab, /lab/worklist, /lab/results, /lab/catalog, /lab/reconciliation",
    status: "absorbed into Experience",
    notes: "Lab routes are represented directly in the Experience shell.",
  },
  {
    sidecarApp: "ui/ops-console",
    capability: "VITO operations and card-management tooling",
    oldUiPath: "ui/ops-console:/dedup,/issuance,/match-queue,/config",
    newExperiencePath: "/operations, /operations/vito",
    status: "absorbed into Experience",
    notes: "Operations workflows now live inside the professional Experience shell.",
  },
  {
    sidecarApp: "ui/inventory-web",
    capability: "Inventory dashboard, counts, movements, and requisitions",
    oldUiPath: "ui/inventory-web:/dashboard,/counts,/movements,/requisitions",
    newExperiencePath: "/inventory, /inventory/counts, /inventory/movements, /inventory/requisitions",
    status: "absorbed into Experience",
    notes: "Inventory already runs fully under Experience.",
  },
  {
    sidecarApp: "ui/support-console",
    capability: "Support tickets, incidents, and knowledge-base operations",
    oldUiPath: "ui/support-console:/tickets,/incidents,/knowledge-base,/messages",
    newExperiencePath: "/support, /support/tickets, /support/knowledge-base, /communication",
    status: "absorbed into Experience",
    notes: "Support and communications are now part of the unified shell.",
  },
  {
    sidecarApp: "ui/developer-console",
    capability: "Developer portal, API catalog, clients, and sandbox",
    oldUiPath: "ui/developer-console:/api-catalog,/clients,/sandbox",
    newExperiencePath: "/developer, /developer/api-catalog, /developer/clients, /developer/sandbox",
    status: "absorbed into Experience",
    notes: "Developer workflows are now canonically routed inside Experience.",
  },
  {
    sidecarApp: "ui/pharmacy-web",
    capability: "Pharmacy dispensing and stock operations",
    oldUiPath: "ui/pharmacy-web:/dispense,/stock,/prescriptions",
    newExperiencePath: "/pharmacy, /pharmacy/dispense, /pharmacy/stock, /pharmacy/prescriptions",
    status: "absorbed into Experience",
    notes: "Pharmacy was already consolidated and remains aligned.",
  },
  {
    sidecarApp: "ui/pct-web",
    capability: "Queue and control-tower operations",
    oldUiPath: "ui/pct-web:/queues,/control-tower",
    newExperiencePath: "/queue, /clinical/control-tower",
    status: "absorbed into Experience",
    notes: "Queue and control-tower workflows are already inside Experience.",
  },
  {
    sidecarApp: "ui/one-ui-shell",
    capability: "Legacy shell scaffolding",
    oldUiPath: "ui/one-ui-shell:/",
    newExperiencePath: "/home",
    status: "retired sidecar path",
    notes: "The canonical shell is ui/experience; one-ui-shell remains only as legacy scaffolding.",
  },
];

export const SIDECAR_STATS = {
  totalSidecars: SIDECAR_RETIREMENT_LEDGER.length,
  absorbed: SIDECAR_RETIREMENT_LEDGER.filter((entry) => entry.status === "absorbed into Experience").length,
  partial: SIDECAR_RETIREMENT_LEDGER.filter((entry) => entry.status === "partially absorbed into Experience").length,
  retired: SIDECAR_RETIREMENT_LEDGER.filter((entry) => entry.status === "retired sidecar path").length,
  blocked: SIDECAR_RETIREMENT_LEDGER.filter((entry) => entry.status === "blocked by missing backend contract").length,
};
