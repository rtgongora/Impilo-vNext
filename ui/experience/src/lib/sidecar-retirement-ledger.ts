export type SidecarRetirementStatus =
  | "absorbed into Experience"
  | "partially absorbed into Experience"
  | "blocked by missing backend contract"
  | "retired sidecar path";

export interface SidecarRetirementEntry {
  capability: string;
  oldUiPath: string;
  newExperiencePath: string;
  status: SidecarRetirementStatus;
  blockerContract?: string;
  notes: string;
}

export const SIDECAR_RETIREMENT_LEDGER: SidecarRetirementEntry[] = [
  {
    capability: "Facility marketplace shell",
    oldUiPath: "ui/msika-flow-portal:/browse,/orders",
    newExperiencePath: "/marketplace,/marketplace/catalog,/marketplace/orders",
    status: "absorbed into Experience",
    notes:
      "Experience marketplace pages are BFF-backed through /internal/v1/marketplace/* for facility-facing browse and order follow-through.",
  },
  {
    capability: "MSIKA catalog governance and publish workflow",
    oldUiPath: "ui/msika-web:/catalogs,/items,/mappings,/publish",
    newExperiencePath: "/finance/commerce-integrations",
    status: "blocked by missing backend contract",
    blockerContract:
      "No /internal/v1/msika/catalogs, /internal/v1/msika/items, /internal/v1/msika/mappings, or publish proxy exists on experience-bff.",
    notes:
      "The accepted Experience surface documents the gap honestly instead of routing operators back to the sidecar as a delivered path.",
  },
  {
    capability: "MSIKA cart, payment, pickup, and substitution flow",
    oldUiPath: "ui/msika-flow-portal:/cart,/orders/{id}/pay,/pickup,/substitutions",
    newExperiencePath: "/finance/commerce-integrations",
    status: "blocked by missing backend contract",
    blockerContract:
      "No Experience BFF routes mirror /v1/cart/validate, /v1/orders/*/pay, pickup, or substitution endpoints.",
    notes:
      "Marketplace parity cannot be claimed for these steps until the BFF exposes canonical internal routes.",
  },
  {
    capability: "Vendor fulfillment queue",
    oldUiPath: "ui/msika-flow-vendor:/queue,/orders,/fulfillment,/substitutions",
    newExperiencePath: "/finance/commerce-integrations",
    status: "blocked by missing backend contract",
    blockerContract:
      "No /internal/v1/msika/vendor/* fulfillment proxy exists in experience-bff.",
    notes: "Keep the sidecar out of accepted delivery until vendor routing is bridged through Experience.",
  },
  {
    capability: "MSIKA operations reviews and stuck-order intervention",
    oldUiPath: "ui/msika-flow-ops:/vendors,/stuck,/orders,/audit,/reviews",
    newExperiencePath: "/finance/commerce-integrations",
    status: "blocked by missing backend contract",
    blockerContract:
      "No Experience BFF router exists for /v1/ops/reviews/pending, stuck orders, vendor suspend/reinstate, or audit feeds.",
    notes: "This remains an integration intake item, not a delivered Experience workflow.",
  },
  {
    capability: "Finance and coverage dashboarding",
    oldUiPath: "ui/mushex-finance-console and ui/mushex-payer-portal",
    newExperiencePath: "/finance,/coverage,/reports,/admin/data-export",
    status: "absorbed into Experience",
    notes:
      "Claims, billing, payments, coverage, and report jobs now run through existing Experience BFF contracts instead of sidecar UIs.",
  },
  {
    capability: "MusheX settlements, reconciliation, refunds, and remittance issue/claim",
    oldUiPath: "ui/mushex-finance-console:/settlements,/ledger,/reconciliation,/refunds and ui/mushex-payer-portal:/payments,/receipts,/remittance",
    newExperiencePath:
      "/finance/settlements,/finance/reconciliation,/finance/refunds,/finance/payer-ops,/finance/commerce-integrations",
    status: "partially absorbed into Experience",
    blockerContract:
      "Finance BFF exposes /internal/v1/finance/settlements/*, /internal/v1/finance/reconciliation/*, /internal/v1/finance/refunds/*, and /internal/v1/finance/payer-ops/* (MusheX upstream). There is still no generic /internal/v1/mushex/* aggregate; MusheX ledger-only or other unmired console paths may remain.",
    notes:
      "Operator workflows for settlements, recon, refunds, and payer remittance intents run in Experience when roles allow; parity with every finance-console tab is not claimed.",
  },
  {
    capability: "MusheX ops fraud and adapter administration",
    oldUiPath: "ui/mushex-ops-console:/claims,/fraud,/reviews,/adapters",
    newExperiencePath: "/finance/payer-ops,/finance/commerce-integrations",
    status: "partially absorbed into Experience",
    blockerContract:
      "Adapters, fraud flags, and ops reviews are available under /internal/v1/finance/payer-ops/*. MusheX claims-centric ops routes not mirrored on that controller may still be absent.",
    notes:
      "Use /finance/payer-ops for the mapped queues; other mushex-ops-console surfaces stay documented on the integration map until contracted.",
  },
  {
    capability: "International Patient Summary (IPS)",
    oldUiPath: "ui/butano-web:/ips",
    newExperiencePath: "/ehr/[patientId]/ips",
    status: "absorbed into Experience",
    notes:
      "Experience now proxies Butano on /internal/v1/summary/ips/{cpid} and renders the chart-scoped FHIR bundle in the EHR shell.",
  },
  {
    capability: "Clinical timeline viewer",
    oldUiPath: "ui/butano-web:/timeline",
    newExperiencePath: "/ehr/[patientId]/timeline",
    status: "retired sidecar path",
    notes:
      "Timeline review already lives in the chart shell, so the Butano sidecar path is no longer the accepted operator entry point.",
  },
  {
    capability: "Butano reconciliation and stats tooling",
    oldUiPath: "ui/butano-web:/reconciliation,/reconciliation/trigger,/stats",
    newExperiencePath: "/admin/integration-status",
    status: "blocked by missing backend contract",
    blockerContract:
      "Experience has no canonical admin route for /v1/internal/reconcile-subject, /v1/internal/reconciliation/{id}, or /v1/internal/stats.",
    notes:
      "Integration status remains honest that no live external integration registry or Butano admin proxy exists yet.",
  },
  {
    capability: "Portal request-ID, QR, delegated pickup, and ID recovery",
    oldUiPath: "ui/portal:/request-id,/my-qr,/pickup,/recovery",
    newExperiencePath: "pending canonical /citizen/* Experience routes",
    status: "blocked by missing backend contract",
    blockerContract:
      "Gateway-relative portal clients exist in the current tree, but no reconciled canonical Experience route/auth intake has been accepted in the red zone yet.",
    notes:
      "Treat these as migration candidates, not accepted Experience delivery, until route canon and auth continuity are reconciled.",
  },
  {
    capability: "Self-service share verification, document claim, and personal credential vault",
    oldUiPath: "ui/self-service:/verify,/claim,/my-documents,/my-credentials",
    newExperiencePath: "pending /share/* and /citizen/* Experience routes",
    status: "blocked by missing backend contract",
    blockerContract:
      "No reconciled Experience BFF or public gateway contract is in canonical routing yet for verify, my-documents, or my-credentials; share claim still needs accepted public-origin configuration.",
    notes:
      "Where local Experience migration files exist, they remain unverified until they are merged and registered through the shared route/auth rails.",
  },
];
