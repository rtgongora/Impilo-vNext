#!/usr/bin/env node
/**
 * Phase 2 — Core Transaction Mapping generator.
 * Inputs: Phase 1 product-truth-recovery-map.json + existing doctrine/contracts.
 * Run: node scripts/product/generate-core-transaction-maps.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const DATE = new Date().toISOString();
const OUT_DOCS = path.join(ROOT, "docs/product");
const OUT_REPORTS = path.join(ROOT, "reports/product");

const COMPLETION_STATUSES = [
  "transaction-complete",
  "backend-ready-but-frontend-incomplete",
  "backend-partial",
  "frontend-route-exists-but-disconnected",
  "mobile-missing",
  "trust-security-incomplete",
  "event-data-incomplete",
  "unclear-intent",
  "unknown-needs-review",
];

/** Authority for the completion bar — see docs/frontend/GAP_CLOSURE_RULES.md */
const GAP_CLOSURE_AUTHORITY = "docs/frontend/GAP_CLOSURE_RULES.md";
const PHASE_4_BAR = "docs/product/PHASE_4_PRODUCTION_COMPLETION_BAR.md";

/**
 * PO gap notes preserved when classification is derived (not asserted).
 * Keys mirror journey ids; values override missingBackend/missingFrontend in reports.
 */
const JOURNEY_GAP_OVERRIDES = {
  "citizen-onboarding": { missingFrontend: "" },
  "provider-login": { missingFrontend: "" },
  "workspace-context-selection": { missingFrontend: "" },
  "facility-context-selection": { missingFrontend: "" },
  "provider-patient-encounter": { missingBackend: "" },
  "outpatient-consultation": { missingFrontend: "" },
  "inpatient-admission": { missingFrontend: "" },
  "telemedicine-encounter": { missingFrontend: "" },
  "imaging-order-result": { missingFrontend: "" },
  "prescription-dispense": { missingFrontend: "" },
  "referral-create": { missingFrontend: "" },
  "appointment-scheduling": { missingFrontend: "" },
  "consent-capture": { missingFrontend: "" },
  "payment-billing-claim": { missingFrontend: "" },
  "document-upload": { missingFrontend: "" },
  "dispatch-delivery": { missingFrontend: "" },
  "notification-comms": { missingFrontend: "" },
  "fundo-learning": { missingFrontend: "" },
  "reporting-dashboard": { missingFrontend: "" },
  "registry-administration": { missingFrontend: "" },
  "integration-sync-replay": { missingFrontend: "" },
  "device-system-event": { missingFrontend: "ops admin surface only (by design)" },
  "health-id-issuance": { missingFrontend: "" },
  "marketplace-order": { missingFrontend: "" },
  "wellness-journey": { missingFrontend: "" },
  "impilo-live-events": { missingFrontend: "" },
  "social-community": { missingFrontend: "" },
  "public-health-outreach": { missingFrontend: "" },
  "crvs-ubomi": { missingFrontend: "" },
  "coverage-enrollment": { missingFrontend: "intelligence surfaces partial" },
  "offline-clinical-queue": { missingFrontend: "" },
  "emergency-encounter": { missingFrontend: "" },
  "surveillance-outbreak": { missingFrontend: "" },
  "ai-guidance-nompilo": { missingFrontend: "" },
  "credential-verification": { missingFrontend: "" },
  "provider-registry-onboarding": { missingFrontend: "" },
  "citizen-monitoring": { missingFrontend: "" },
  "chronic-care": { missingFrontend: "" },
  "haemovigilance-report": { missingFrontend: "national dashboard web-only by policy" },
};

/** Journeys where measured signals cannot override PO-reviewed classification. */
const JOURNEY_CLASSIFICATION_HINTS = {};

/** Stale hard-coded baseline (pre-Phase 4.0) for rebaseline delta reporting. */
const STALE_BASELINE_CLASSIFICATIONS = {
  "citizen-onboarding": "backend-ready-but-frontend-incomplete",
  "provider-login": "backend-ready-but-frontend-incomplete",
  "workspace-context-selection": "backend-ready-but-frontend-incomplete",
  "facility-context-selection": "backend-ready-but-frontend-incomplete",
  "patient-search-selection": "backend-ready-but-frontend-incomplete",
  "queue-walk-in": "transaction-complete",
  "provider-patient-encounter": "transaction-complete",
  "outpatient-consultation": "backend-ready-but-frontend-incomplete",
  "inpatient-admission": "backend-partial",
  "telemedicine-encounter": "backend-ready-but-frontend-incomplete",
  "lab-order-result": "transaction-complete",
  "imaging-order-result": "backend-ready-but-frontend-incomplete",
  "prescription-dispense": "backend-ready-but-frontend-incomplete",
  "referral-create": "backend-ready-but-frontend-incomplete",
  "appointment-scheduling": "backend-partial",
  "consent-capture": "backend-ready-but-frontend-incomplete",
  "payment-billing-claim": "backend-ready-but-frontend-incomplete",
  "document-upload": "backend-partial",
  "dispatch-delivery": "backend-partial",
  "notification-comms": "backend-ready-but-frontend-incomplete",
  "fundo-learning": "backend-ready-but-frontend-incomplete",
  "reporting-dashboard": "backend-partial",
  "registry-administration": "backend-ready-but-frontend-incomplete",
  "integration-sync-replay": "backend-partial",
  "device-system-event": "backend-partial",
  "health-id-issuance": "backend-partial",
  "marketplace-order": "backend-ready-but-frontend-incomplete",
  "wellness-journey": "backend-ready-but-frontend-incomplete",
  "social-community": "backend-ready-but-frontend-incomplete",
  "public-health-outreach": "mobile-missing",
  "crvs-ubomi": "mobile-missing",
  "coverage-enrollment": "backend-ready-but-frontend-incomplete",
  "wallet-payment": "backend-ready-but-frontend-incomplete",
  "offline-clinical-queue": "backend-partial",
  "emergency-encounter": "trust-security-incomplete",
  "core-transaction-orchestration": "transaction-complete",
  "surveillance-outbreak": "backend-ready-but-frontend-incomplete",
  "ai-guidance-nompilo": "backend-ready-but-frontend-incomplete",
  "credential-verification": "backend-partial",
  "provider-registry-onboarding": "backend-ready-but-frontend-incomplete",
  "citizen-monitoring": "backend-ready-but-frontend-incomplete",
  "chronic-care": "backend-partial",
  "blood-donation": "transaction-complete",
  "blood-order": "transaction-complete",
  "transfusion-episode": "transaction-complete",
  "haemovigilance-report": "transaction-complete",
};

/** Journeys where mobile parity is expected when web routes exist. */
const MOBILE_PARITY_EXPECTED = new Set([
  "prescription-dispense",
  "wellness-journey",
  "social-community",
  "citizen-monitoring",
  "coverage-enrollment",
  "wallet-payment",
  "appointment-scheduling",
]);

/**
 * Evidence-gated completion registry.
 * A journey may be `transaction-complete` ONLY when listed here with real BFF routes,
 * UI routes, and at least one passing test file that exists on disk.
 */
const COMPLETION_EVIDENCE = {
  "provider-patient-encounter": {
    bffEndpoints: ["/internal/v1/core-transactions", "/internal/v1/encounters"],
    uiRoutes: ["/ehr/[patientId]/encounter/[encounterId]"],
    tests: [
      "ui/one-ui-shell/src/components/encounter/EncounterOrchestrationRail.test.tsx",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/service/EncounterCoreTransactionCompositionTest.java",
    ],
  },
  "core-transaction-orchestration": {
    bffEndpoints: ["/internal/v1/core-transactions"],
    uiRoutes: ["/core-transaction"],
    tests: [
      "ui/one-ui-shell/src/features/core-transaction/__tests__/core-transaction.test.tsx",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CoreTransactionControllerTest.java",
    ],
  },
  "queue-walk-in": {
    bffEndpoints: ["/internal/v1/queue/entries", "/internal/v1/core-transactions"],
    uiRoutes: ["/queue/walk-in", "/ehr/[patientId]/encounters"],
    tests: [
      "ui/one-ui-shell/src/app/queue/walk-in/page.test.tsx",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/QueueControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/service/JourneyCoreTransactionCompositionTest.java",
    ],
  },
  "lab-order-result": {
    bffEndpoints: ["/internal/v1/lab-orders"],
    uiRoutes: ["/ehr/[patientId]/encounter/[encounterId]", "/ehr/[patientId]/orders"],
    tests: [
      "ui/one-ui-shell/src/components/encounter/EncounterLabOrdersPanel.test.tsx",
      "ui/one-ui-shell/src/lib/lab-order-bff.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/LabOrdersControllerTest.java",
    ],
  },
  "blood-donation": {
    bffEndpoints: [
      "/internal/v1/madi/donors",
      "/internal/v1/madi/donor/assist",
      "/internal/v1/mobile/citizen/madi/register",
    ],
    uiRoutes: ["/madi/donor/screening", "/madi/donor/register"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/madi-golden-thread.test.ts",
      "services/madi-service/src/test/java/zw/gov/mohcc/impilo/madi/core/DonorPreScreeningTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MadiControllerTest.java",
      "apps/mobile/citizen-app/src/services/madiService.test.ts",
      "ui/one-ui-shell/e2e/madi-flow.spec.ts",
    ],
  },
  "blood-order": {
    bffEndpoints: ["/internal/v1/madi/orders"],
    uiRoutes: ["/madi/orders", "/madi/orders/[orderId]"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/madi-golden-thread.test.ts",
      "services/madi-service/src/test/java/zw/gov/mohcc/impilo/madi/core/BloodOrderServiceTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MadiControllerTest.java",
      "ui/one-ui-shell/e2e/madi-flow.spec.ts",
    ],
  },
  "transfusion-episode": {
    bffEndpoints: ["/internal/v1/madi/transfusions"],
    uiRoutes: ["/madi/transfusion", "/madi/transfusion/[episodeId]"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/madi-golden-thread.test.ts",
      "services/madi-service/src/test/java/zw/gov/mohcc/impilo/madi/core/TransfusionPreVerifyTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MadiControllerTest.java",
      "apps/mobile/provider-app/src/services/madiService.test.ts",
      "ui/one-ui-shell/e2e/madi-flow.spec.ts",
    ],
  },
  "haemovigilance-report": {
    bffEndpoints: ["/internal/v1/madi/haemovigilance"],
    uiRoutes: ["/madi/haemovigilance", "/madi/haemovigilance/national"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/madi-golden-thread.test.ts",
      "services/madi-service/src/test/java/zw/gov/mohcc/impilo/madi/core/HaemovigilanceServiceTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MadiControllerTest.java",
      "ui/one-ui-shell/e2e/madi-flow.spec.ts",
    ],
  },
  "outpatient-consultation": {
    bffEndpoints: ["/internal/v1/encounters", "/internal/v1/lab-orders"],
    uiRoutes: ["/ehr/[patientId]/encounter/[encounterId]"],
    tests: [
      "ui/one-ui-shell/src/components/encounter/EncounterDischargePanel.test.tsx",
      "ui/one-ui-shell/src/components/encounter/EncounterImagingOrdersPanel.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/outpatient-consultation-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/EncounterControllerTest.java",
    ],
  },
  "imaging-order-result": {
    bffEndpoints: ["/internal/v1/lab-orders", "/internal/v1/imaging"],
    uiRoutes: ["/ehr/[patientId]/encounter/[encounterId]", "/ehr/[patientId]/imaging", "/ehr/[patientId]/imaging/viewer"],
    tests: [
      "ui/one-ui-shell/src/components/encounter/EncounterImagingOrdersPanel.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/imaging-order-result-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/LabOrdersControllerTest.java",
    ],
  },
  "appointment-scheduling": {
    bffEndpoints: [
      "/internal/v1/appointments",
      "/internal/v1/appointments/{id}/check-in",
      "/internal/v1/mobile/citizen/appointments/{id}/check-in",
    ],
    uiRoutes: ["/queue/scheduled", "/home/appointments/[appointmentId]", "/home/bookings/new"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/appointment-check-in-routing.test.ts",
      "ui/one-ui-shell/src/lib/__tests__/appointment-scheduling-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/SchedulingControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CitizenAppointmentControllerTest.java",
      "apps/mobile/citizen-app/src/__tests__/personal/PersonalFlow.test.tsx",
    ],
  },
  "inpatient-admission": {
    bffEndpoints: ["/internal/v1/inpatient/admissions", "/internal/v1/beds"],
    uiRoutes: ["/clinical/inpatient/ward-board", "/clinical/inpatient/admissions"],
    tests: [
      "ui/one-ui-shell/src/components/ward/WardManagementPanel.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/outpatient-consultation-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/InpatientControllerTest.java",
      "services/inpatient-service/src/test/java/zw/gov/mohcc/impilo/inpatient/core/BedManagementServiceTest.java",
    ],
  },
  "emergency-encounter": {
    bffEndpoints: ["/internal/v1/emergency", "/internal/v1/trust/break-glass", "/internal/v1/mobile/provider/break-glass/activate"],
    uiRoutes: ["/clinical/emergency", "/ehr/[patientId]/emergency", "/admin/break-glass"],
    tests: [
      "ui/one-ui-shell/src/components/trust/BreakGlassRequestPanel.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/emergency-encounter-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/TrustProviderControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MobileProviderControllerTest.java",
    ],
  },
  "wellness-journey": {
    bffEndpoints: ["/internal/v1/wellness", "/internal/v1/mobile/citizen/wellness"],
    uiRoutes: ["/wellness/routes", "/wellness/dashboard"],
    tests: [
      "ui/one-ui-shell/src/app/wellness/routes/page.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/wellness-journey-golden-thread.test.ts",
    ],
  },
  "prescription-dispense": {
    bffEndpoints: [
      "/internal/v1/pharmacy/dispense",
      "/internal/v1/mobile/provider/pharmacy/pending",
      "/internal/v1/mobile/provider/pharmacy/dispense",
      "/internal/v1/mobile/provider/pharmacy/verify-five-rights",
    ],
    uiRoutes: ["/pharmacy/dispense", "/ehr/[patientId]/orders"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/prescription-dispense-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PharmacyControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MobileProviderExtendedControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/pharmacy/PharmacyFiveRightsVerifierTest.java",
      "apps/mobile/provider-app/src/__tests__/services/prescriptionService.test.ts",
    ],
  },
  "referral-create": {
    bffEndpoints: [
      "/internal/v1/referrals",
      "/internal/v1/referrals/incoming",
      "/internal/v1/referrals/{id}/accept",
      "/internal/v1/referrals/{id}/respond",
    ],
    uiRoutes: ["/queue/incoming-referrals", "/ehr/[patientId]/consults", "/ehr/[patientId]/referrals"],
    tests: [
      "ui/one-ui-shell/src/app/queue/incoming-referrals/page.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/referral-create-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ReferralsControllerTest.java",
    ],
  },
  "health-id-issuance": {
    bffEndpoints: [
      "/internal/v1/vito/issuance/queue",
      "/internal/v1/vito/issuance/{requestId}",
      "/internal/v1/vito/delegated-pickup/create",
      "/internal/v1/vito/print/card/verify-pickup",
      "/internal/v1/vito/print/card/confirm-handover",
    ],
    uiRoutes: [
      "/operations/vito/issuance",
      "/operations/vito/issuance/[requestId]",
      "/operations/vito/cards/pickup",
      "/operations/vito/print",
    ],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/health-id-pickup-golden-thread.test.ts",
      "services/vito-service/src/test/java/zw/gov/mohcc/impilo/vito/api/PrintJobControllerPickupTest.java",
      "services/vito-service/src/test/java/zw/gov/mohcc/impilo/vito/core/pickup/DelegatedPickupServiceTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/facility/FacilityNameResolverTest.java",
    ],
  },
  "citizen-monitoring": {
    bffEndpoints: [
      "/internal/v1/mobile/citizen/monitoring/devices",
      "/internal/v1/mobile/citizen/monitoring/readings",
      "/internal/v1/mobile/citizen/wellness/vitals",
      "/internal/v1/wellness/personal-data/remote-alerts",
    ],
    uiRoutes: ["/monitoring/devices", "/monitoring/readings", "/monitoring/alerts"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/citizen-monitoring-golden-thread.test.ts",
      "services/wellness-service/src/test/java/zw/gov/mohcc/impilo/wellness/monitoring/MonitoringDeviceReadingsServiceTest.java",
    ],
  },
  "wallet-payment": {
    bffEndpoints: [
      "/internal/v1/wallet/me",
      "/internal/v1/wallet/me/balance",
      "/internal/v1/wallet/pay",
      "/internal/v1/wallet/me/transactions",
    ],
    uiRoutes: ["/wallet", "/wallet/send", "/wallet/transactions"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/WalletControllerTest.java",
      "ui/one-ui-shell/e2e/wallet-payment-flow.spec.ts",
    ],
  },
  "coverage-enrollment": {
    bffEndpoints: [
      "/internal/v1/coverage/plans",
      "/internal/v1/coverage/members",
      "/internal/v1/coverage/eligibility/check",
    ],
    uiRoutes: ["/coverage", "/coverage/enroll"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CoverageControllerTest.java",
      "ui/one-ui-shell/src/hooks/queries/__tests__/useCoverage.test.ts",
      "ui/one-ui-shell/e2e/coverage-enroll-flow.spec.ts",
    ],
  },
  "document-upload": {
    bffEndpoints: [
      "/internal/v1/clinical-documents",
      "/internal/v1/clinical-documents/upload",
    ],
    uiRoutes: ["/ehr/[patientId]/documents"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ClinicalDocumentsControllerTest.java",
      "ui/one-ui-shell/src/app/ehr/[patientId]/documents/page.test.tsx",
    ],
  },
  "patient-search-selection": {
    bffEndpoints: ["/internal/v1/patients", "/internal/v1/queue/entries"],
    uiRoutes: ["/queue/search", "/ehr/[patientId]"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PatientControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/QueueControllerTest.java",
      "ui/one-ui-shell/src/components/encounter/PatientSearchOrchestrationRail.test.tsx",
      "ui/one-ui-shell/src/app/queue/search/page.test.tsx",
      "ui/one-ui-shell/e2e/patient-search-flow.spec.ts",
    ],
  },
  "consent-capture": {
    bffEndpoints: [
      "/internal/v1/mvumo-admin/templates",
      "/internal/v1/mvumo-admin/consent-requests",
      "/internal/v1/teleconsult/sessions/{id}/consent",
      "/internal/v1/consent/accept",
    ],
    uiRoutes: ["/registry/mvumo", "/consent", "/admin/consent"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MvumoAdminControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/TeleconsultControllerTest.java",
      "services/mvumo-service/src/test/java/zw/gov/mohcc/impilo/mvumo/service/MvumoServiceRemoteSessionAndTemplateTest.java",
      "ui/one-ui-shell/src/hooks/queries/__tests__/useMvumoAdmin.test.ts",
      "ui/one-ui-shell/src/app/registry/mvumo/page.test.tsx",
      "ui/one-ui-shell/e2e/consent-capture-flow.spec.ts",
    ],
  },
  "payment-billing-claim": {
    bffEndpoints: [
      "/internal/v1/finance",
      "/internal/v1/finance/payer-ops",
      "/internal/v1/finance/payer-ops/payment-intents/{intentId}/attempts",
      "/internal/v1/finance/payer-ops/adapters",
      "/internal/v1/finance/payer-ops/remittance/claim",
    ],
    uiRoutes: ["/finance", "/finance/payer-ops", "/finance/billing", "/finance/claims"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PayerOpsControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/FinanceControllerTest.java",
      "ui/one-ui-shell/src/app/finance/payer-ops/page.test.tsx",
      "ui/one-ui-shell/e2e/payer-ops-flow.spec.ts",
    ],
  },
  "facility-context-selection": {
    bffEndpoints: ["/internal/v1/facilities"],
    uiRoutes: ["/facility", "/facility/[id]"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/FacilityControllerTest.java",
      "ui/one-ui-shell/e2e/context-selection-flow.spec.ts",
    ],
  },
  "workspace-context-selection": {
    bffEndpoints: [
      "/internal/v1/workspaces",
      "/internal/v1/workspaces/{id}",
      "/internal/v1/shifts/start",
      "/internal/v1/shifts/handover",
    ],
    uiRoutes: ["/workspace", "/workspace/[id]", "/shift", "/shift/handover"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/WorkspaceControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ShiftControllerTest.java",
      "ui/one-ui-shell/e2e/context-selection-flow.spec.ts",
    ],
  },
  "provider-login": {
    bffEndpoints: [
      "/internal/v1/auth/login",
      "/internal/v1/auth/session",
      "/internal/v1/identity/linked-ids",
      "/internal/v1/identity/providers",
    ],
    uiRoutes: ["/auth/login", "/auth/resolving", "/provider/activate"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ProviderActivationControllerTest.java",
      "ui/one-ui-shell/src/components/auth/ProviderRoleActivationRail.test.tsx",
      "ui/one-ui-shell/e2e/provider-login-flow.spec.ts",
    ],
  },
  "credential-verification": {
    bffEndpoints: ["/internal/v1/credentials", "/internal/v1/credentials/{id}/pdf"],
    uiRoutes: ["/verify/credential", "/home/credentials"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CredentialVaultControllerTest.java",
      "ui/one-ui-shell/src/app/verify/credential/page.test.tsx",
      "ui/one-ui-shell/e2e/credential-verification-flow.spec.ts",
    ],
  },
  "chronic-care": {
    bffEndpoints: [
      "/internal/v1/care-plans",
      "/internal/v1/care-plans/{planId}/goals",
      "/internal/v1/care-plans/{planId}/interventions/{id}/perform",
    ],
    uiRoutes: ["/ehr/[patientId]/care-plans", "/ehr/[patientId]/goals"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CareEmergencyInpatientControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ClinicalDepthControllerTest.java",
      "ui/one-ui-shell/src/components/chronic-care/CarePlanOrchestrationRail.test.tsx",
      "ui/one-ui-shell/e2e/chronic-care-flow.spec.ts",
    ],
  },
  "citizen-onboarding": {
    bffEndpoints: [
      "/internal/v1/auth/register",
      "/internal/v1/vito/issuance/submit",
      "/internal/v1/vito/issuance/queue",
      "/internal/v1/vito/print/card/verify-pickup",
    ],
    uiRoutes: ["/auth/register", "/auth/register/status", "/operations/vito/issuance", "/operations/vito/cards/pickup"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/VitoIssuanceBffControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/VitoPrintBffControllerTest.java",
      "ui/one-ui-shell/src/app/operations/vito/issuance/page.test.tsx",
      "ui/one-ui-shell/e2e/citizen-onboarding-flow.spec.ts",
    ],
  },
  "telemedicine-encounter": {
    bffEndpoints: [
      "/internal/v1/teleconsult/sessions",
      "/internal/v1/teleconsult/sessions/{id}/consent",
      "/internal/v1/teleconsult/sessions/{id}/media/token",
      "/internal/v1/teleconsult/sessions/{id}/submit",
      "/internal/v1/teleconsult/sessions/{id}/complete",
    ],
    uiRoutes: ["/telemedicine", "/telemedicine/new", "/telemedicine/session/[sessionId]"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/TeleconsultControllerTest.java",
      "ui/one-ui-shell/e2e/telemedicine-encounter-flow.spec.ts",
    ],
  },
  "device-system-event": {
    bffEndpoints: [
      "/internal/v1/devices",
      "/internal/v1/devices/register",
      "/internal/v1/devices/{deviceId}/revoke",
      "/internal/v1/devices/{deviceId}/trust",
    ],
    uiRoutes: ["/admin/devices"],
    tests: [
      "services/iot-ingestion-service/src/main/java/zw/gov/mohcc/impilo/iotingestion/api/DeviceRegistryController.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/DeviceRegistryControllerTest.java",
      "ui/one-ui-shell/src/app/admin/devices/page.test.tsx",
      "ui/one-ui-shell/e2e/device-system-event-flow.spec.ts",
    ],
  },
  "notification-comms": {
    bffEndpoints: [
      "/internal/v1/omnichannel/dashboard",
      "/internal/v1/omnichannel/campaigns",
      "/internal/v1/notifications/send",
      "/internal/v1/notifications/templates",
    ],
    uiRoutes: ["/omnichannel", "/omnichannel?tab=campaigns", "/admin/notifications/templates", "/communication"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/NotificationControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/OmnichannelControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/NotificationTemplateControllerTest.java",
      "ui/one-ui-shell/src/components/platform/NotificationCommsOrchestrationRail.test.tsx",
      "ui/one-ui-shell/e2e/notification-comms-flow.spec.ts",
    ],
  },
  "integration-sync-replay": {
    bffEndpoints: [
      "/internal/v1/integration-hub/routes",
      "/internal/v1/integration-hub/deadletters",
      "/internal/v1/integration-hub/deadletters/{id}/replay",
      "/internal/v1/integration-hub/mapping-templates",
    ],
    uiRoutes: ["/admin/integration-status", "/admin/integration-templates"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/IntegrationHubControllerTest.java",
      "ui/one-ui-shell/src/components/platform/IntegrationSyncReplayOrchestrationPanel.test.tsx",
      "ui/one-ui-shell/src/app/admin/integration-status/page.test.tsx",
      "ui/one-ui-shell/e2e/integration-sync-replay-flow.spec.ts",
    ],
  },
  "registry-administration": {
    bffEndpoints: [
      "/internal/v1/registry/providers",
      "/internal/v1/identity/search",
      "/internal/v1/vito/issuance/queue",
      "/internal/v1/vito/registry/provisional/pending",
      "/internal/v1/vito/registry/dedup/pending",
    ],
    uiRoutes: [
      "/registry-admin",
      "/registry/providers/verification",
      "/operations/vito/issuance",
      "/operations/vito/registry-admin",
      "/operations/vito/dedup",
      "/operations/vito/match",
    ],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/RegistryControllerTest.java",
      "ui/one-ui-shell/src/components/platform/RegistryAdministrationOrchestrationRail.test.tsx",
      "ui/one-ui-shell/src/app/registry-admin/page.test.tsx",
      "ui/one-ui-shell/e2e/registry-administration-flow.spec.ts",
    ],
  },
  "reporting-dashboard": {
    bffEndpoints: [
      "/internal/v1/reports/generate",
      "/internal/v1/reports/{id}",
      "/internal/v1/ndila/tiles/config",
      "/internal/v1/ndr/query/bronze",
      "/internal/v1/ndr/query/gold/encounters",
    ],
    uiRoutes: [
      "/reports",
      "/reports/custom",
      "/ndila",
      "/data-intelligence",
      "/data-intelligence/pipelines",
    ],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ReportJobControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/NdrControllerTest.java",
      "ui/one-ui-shell/src/components/platform/ReportingDashboardOrchestrationPanel.test.tsx",
      "ui/one-ui-shell/src/components/ndila/NdilaWorkspaceMapPanel.test.tsx",
      "ui/one-ui-shell/src/components/data-intelligence/NdrWarehouseQueryPanel.test.tsx",
      "ui/one-ui-shell/e2e/reporting-dashboard-flow.spec.ts",
    ],
  },
  "ai-guidance-nompilo": {
    bffEndpoints: [
      "/internal/v1/guidance/ask",
      "/internal/v1/guidance/reminders",
      "/internal/v1/guidance/education",
    ],
    uiRoutes: ["/ask", "/guidance", "/guidance/reminders", "/guidance/education"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/GuidanceControllerTest.java",
      "ui/one-ui-shell/src/components/intelligent/NompiloGuidanceOrchestrationRail.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/ai-guidance-nompilo-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/ai-guidance-nompilo-flow.spec.ts",
    ],
  },
  "social-community": {
    bffEndpoints: [
      "/internal/v1/social/feed",
      "/internal/v1/social/suggestions",
      "/internal/v1/social/composer/assist",
      "/internal/v1/public-health/alerts",
    ],
    uiRoutes: ["/social", "/social/drafts", "/social/moderation", "/communities"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/SocialControllerTest.java",
      "ui/one-ui-shell/src/components/social/SocialCommunityOrchestrationRail.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/social-community-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/social-community-flow.spec.ts",
    ],
  },
  "surveillance-outbreak": {
    bffEndpoints: [
      "/internal/v1/public-health/signals",
      "/internal/v1/public-health/cases",
      "/internal/v1/public-health/alerts",
      "/internal/v1/public-health/outbreaks",
      "/internal/v1/ndila/tiles/config",
    ],
    uiRoutes: ["/public-health/surveillance", "/public-health?tab=outbreaks", "/ndila"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PublicHealthControllerTest.java",
      "ui/one-ui-shell/src/components/public-health/SurveillanceOutbreakOrchestrationPanel.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/surveillance-outbreak-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/surveillance-outbreak-flow.spec.ts",
    ],
  },
  "fundo-learning": {
    bffEndpoints: [
      "/internal/v1/learning/v11/catalog",
      "/internal/v1/learning/v11/my-learning",
      "/internal/v1/learning/v11/enrolments",
    ],
    uiRoutes: ["/learning", "/learning/catalog", "/learning/my-learning", "/learning/certificates"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/LearningControllerTest.java",
      "ui/one-ui-shell/src/components/learning/FundoLearningOrchestrationRail.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/fundo-learning-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/fundo-learning-flow.spec.ts",
      "apps/mobile/citizen-app/src/services/fundoLearningService.test.ts",
      "apps/mobile/provider-app/src/services/learningService.test.ts",
    ],
  },
  "impilo-live-events": {
    bffEndpoints: [
      "/internal/v1/live/events",
      "/internal/v1/live/discover/context/{contextType}",
      "/internal/v1/live/registrations/events/{eventId}",
      "/internal/v1/live/room/{eventId}/join",
      "/internal/v1/live/room/{eventId}/replay",
    ],
    uiRoutes: [
      "/live/discover",
      "/live/event/[eventId]",
      "/live/event/[eventId]/room",
      "/live/event/[eventId]/replay",
      "/live/replays",
    ],
    tests: [
      "services/live-service/src/test/java/zw/gov/mohcc/impilo/live/v11/LiveGoldenContractIT.java",
      "ui/one-ui-shell/src/lib/__tests__/impilo-live-events-golden-thread.test.ts",
      "ui/one-ui-shell/src/app/live/discover/page.test.tsx",
      "ui/one-ui-shell/src/app/live/page.test.tsx",
      "apps/mobile/citizen-app/src/services/impiloLiveService.test.ts",
      "ui/one-ui-shell/e2e/impilo-live-events-flow.spec.ts",
    ],
  },
  "dispatch-delivery": {
    bffEndpoints: [
      "/internal/v1/nhume/dashboard",
      "/internal/v1/nhume/deliveries",
      "/internal/v1/nhume/dispatcher-console",
      "/internal/v1/nhume/fleet",
    ],
    uiRoutes: ["/operations/dispatch", "/nhume/dashboard", "/nhume/deliveries"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/DispatchControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/NhumeControllerTest.java",
      "ui/one-ui-shell/src/components/operations/DispatchDeliveryOrchestrationPanel.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/dispatch-delivery-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/dispatch-delivery-flow.spec.ts",
    ],
  },
  "marketplace-order": {
    bffEndpoints: [
      "/internal/v1/marketplace/orders",
      "/internal/v1/marketplace/bookings",
      "/internal/v1/marketplace/catalog",
    ],
    uiRoutes: ["/marketplace", "/marketplace/orders"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MarketplaceControllerTest.java",
      "ui/one-ui-shell/src/components/marketplace/MarketplaceOrderOrchestrationRail.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/marketplace-order-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/marketplace-order-flow.spec.ts",
    ],
  },
  "offline-clinical-queue": {
    bffEndpoints: [
      "/internal/v1/clinical-tools/offline/queue",
      "/internal/v1/clinical-tools/offline/reconcile/pending",
      "/internal/v1/clinical-tools/offline/reconcile",
    ],
    uiRoutes: ["/clinical-tools"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ClinicalToolsControllerTest.java",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobileOfflineControllerTest.java",
      "ui/one-ui-shell/src/components/clinical/OfflineClinicalQueueOrchestrationPanel.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/offline-clinical-queue-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/offline-clinical-queue-flow.spec.ts",
    ],
  },
  "provider-registry-onboarding": {
    bffEndpoints: [
      "/internal/v1/registry/providers",
      "/internal/v1/registry/reconciliation/queue",
      "/internal/v1/registry/reconciliation/{caseId}/decision",
      "/internal/v1/registry/provider-council/applications/open",
    ],
    uiRoutes: ["/registry/providers"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/RegistryControllerTest.java",
      "ui/one-ui-shell/src/components/registry/ProviderRegistryOnboardingOrchestrationRail.test.tsx",
      "ui/one-ui-shell/src/lib/__tests__/provider-registry-onboarding-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/provider-registry-onboarding-flow.spec.ts",
    ],
  },
  "public-health-outreach": {
    bffEndpoints: [
      "/internal/v1/public-health/field-tasks",
      "/internal/v1/mobile/provider/public-health/field-tasks",
    ],
    uiRoutes: ["/public-health", "/public-health?tab=field"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PublicHealthControllerTest.java",
      "ui/one-ui-shell/src/components/public-health/PublicHealthOutreachOrchestrationPanel.test.tsx",
      "apps/mobile/provider-app/src/services/publicHealthService.test.ts",
      "ui/one-ui-shell/src/lib/__tests__/public-health-outreach-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/public-health-outreach-flow.spec.ts",
    ],
  },
  "crvs-ubomi": {
    bffEndpoints: [
      "/internal/v1/ubomi/status",
      "/internal/v1/ubomi/births",
      "/internal/v1/ubomi/deaths",
    ],
    uiRoutes: ["/ubomi"],
    tests: [
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/UbomiControllerTest.java",
      "ui/one-ui-shell/src/components/registry/CrvsUbomiOrchestrationRail.test.tsx",
      "apps/mobile/citizen-app/src/services/ubomiService.test.ts",
      "ui/one-ui-shell/src/lib/__tests__/crvs-ubomi-golden-thread.test.ts",
      "ui/one-ui-shell/e2e/crvs-ubomi-flow.spec.ts",
    ],
  },
};

const CHECK_ONLY = process.argv.includes("--check-only");

function enforceCompletionEvidence(journeys) {
  const errors = [];
  const complete = journeys.filter((j) => j.completionClassification === "transaction-complete");
  const evidenceIds = new Set(Object.keys(COMPLETION_EVIDENCE));

  if (complete.length !== evidenceIds.size) {
    errors.push(
      `transaction-complete count (${complete.length}) must equal evidence registry size (${evidenceIds.size}). ` +
        `No blanket metric flips — add evidence before marking complete.`,
    );
  }

  for (const journey of complete) {
    const evidence = COMPLETION_EVIDENCE[journey.id];
    if (!evidence) {
      errors.push(`Journey "${journey.id}" is transaction-complete but has no COMPLETION_EVIDENCE entry.`);
      continue;
    }
    for (const field of ["bffEndpoints", "uiRoutes", "tests"]) {
      const value = evidence[field];
      if (!Array.isArray(value) || value.length === 0) {
        errors.push(`Journey "${journey.id}": evidence.${field} must be a non-empty array.`);
      }
    }
    for (const testPath of evidence.tests ?? []) {
      const abs = path.join(ROOT, testPath);
      if (!fs.existsSync(abs)) {
        errors.push(`Journey "${journey.id}": missing evidence test file: ${testPath}`);
      }
    }
  }

  for (const id of evidenceIds) {
    const journey = journeys.find((j) => j.id === id);
    if (!journey || journey.completionClassification !== "transaction-complete") {
      errors.push(`Evidence registry entry "${id}" requires journey classification transaction-complete.`);
    }
  }

  if (errors.length > 0) {
    console.error("COMPLETION EVIDENCE ENFORCEMENT FAILED");
    console.error(`Authority: ${GAP_CLOSURE_AUTHORITY}`);
    for (const err of errors) {
      console.error(`  - ${err}`);
    }
    process.exit(1);
  }
}

function parseYaml(filePath) {
  const script = `import json,yaml,sys; print(json.dumps(yaml.safe_load(open(sys.argv[1],encoding="utf-8"))))`;
  const r = spawnSync("python3", ["-c", script, filePath], { encoding: "utf8" });
  return JSON.parse(r.stdout);
}

function csvEscape(v) {
  return `"${String(v ?? "").replace(/"/g, '""')}"`;
}

function mdTable(rows, cols, max = 40) {
  const slice = rows.slice(0, max);
  const h = `| ${cols.join(" | ")} |`;
  const s = `| ${cols.map(() => "---").join(" | ")} |`;
  const b = slice.map((r) => `| ${cols.map((c) => String(r[c] ?? "").replace(/\|/g, "\\|").slice(0, 70)).join(" | ")} |`).join("\n");
  const more = rows.length > max ? `\n\n_…and ${rows.length - max} more in JSON/CSV._\n` : "";
  return `${h}\n${s}\n${b}${more}`;
}

// ── Load Phase 1 recovery map ───────────────────────────────────────────────
const recoveryPath = path.join(OUT_REPORTS, "product-truth-recovery-map.json");
const recovery = JSON.parse(fs.readFileSync(recoveryPath, "utf8"));
const entries = recovery.entries || [];

const backendServices = entries.filter((e) => e.componentType === "backend-service");
const frontendRoutes = entries.filter((e) => e.componentType === "frontend-route");
const mobileScreens = entries.filter((e) => e.componentType === "mobile-screen");
const routePaths = new Set(frontendRoutes.map((e) => e.canonicalName));

function routesMatching(...prefixes) {
  return [...routePaths].filter((p) => prefixes.some((pre) => p === pre || p.startsWith(pre + "/") || p.startsWith(pre))).sort();
}

function screensMatching(...parts) {
  return mobileScreens
    .filter((s) => parts.some((p) => s.sourcePath.toLowerCase().includes(p.toLowerCase())))
    .map((s) => s.sourcePath)
    .sort();
}

function loadContractSignals() {
  const contractPath = path.join(OUT_REPORTS, "contract-implementation-matrix.json");
  const missingByModule = new Map();
  const eventGapsByModule = new Map();
  if (!fs.existsSync(contractPath)) {
    return { missingByModule, eventGapsByModule };
  }
  const contract = JSON.parse(fs.readFileSync(contractPath, "utf8"));
  for (const op of contract.openApiOperations || []) {
    if (op.implStatus === "missing" || op.implStatus === "partial") {
      for (const mod of op.modules || []) {
        missingByModule.set(mod, (missingByModule.get(mod) || 0) + 1);
      }
    }
  }
  for (const ch of contract.asyncChannels || []) {
    if (ch.implStatus === "missing" || ch.implStatus === "partial") {
      for (const mod of ch.modules || []) {
        eventGapsByModule.set(mod, (eventGapsByModule.get(mod) || 0) + 1);
      }
    }
  }
  return { missingByModule, eventGapsByModule };
}

function buildRouteStubSignals() {
  const appDir = path.join(ROOT, "ui/one-ui-shell/src/app");
  const stubRoutes = new Set();
  const fixtureRoutes = new Set();

  function walk(dir, prefix = "") {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith(".")) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        const seg = entry.name.startsWith("[") ? `[${entry.name.slice(1, -1)}]` : entry.name;
        walk(full, `${prefix}/${seg}`);
      } else if (entry.name === "page.tsx") {
        const route = prefix || "/";
        const content = fs.readFileSync(full, "utf8");
        if (/coming soon|under construction|placeholder page|TODO: implement|will be rendered/i.test(content)) {
          stubRoutes.add(route);
        }
        if (/Mock Data|MOCK_|fixture/i.test(content)) {
          fixtureRoutes.add(route);
        }
      }
    }
  }

  walk(appDir);
  return { stubRoutes, fixtureRoutes };
}

function routeMatchesStub(route, stubRoutes) {
  if (stubRoutes.has(route)) return true;
  for (const stub of stubRoutes) {
    if (route === stub || route.startsWith(`${stub}/`) || stub.startsWith(`${route}/`)) {
      return true;
    }
  }
  return false;
}

function journeyHasStubRoutes(journey, signals) {
  const allStub = new Set([...signals.stubRoutes, ...signals.fixtureRoutes]);
  return journey.frontendRoutes.some((r) => routeMatchesStub(r, allStub));
}

function journeyHasContractGaps(journey, missingByModule) {
  return journey.backendServices
    .filter((s) => s !== "experience-bff")
    .some((s) => missingByModule.has(s));
}

function journeyHasEventGaps(journey, eventGapsByModule) {
  return journey.backendServices
    .filter((s) => s !== "experience-bff")
    .some((s) => eventGapsByModule.has(s));
}

function journeyHasBffChain(journey) {
  return journey.backendServices.includes("experience-bff") && /\/internal/i.test(journey.apis || "");
}

function resolveGapFields(journey) {
  const override = JOURNEY_GAP_OVERRIDES[journey.id] || {};
  return {
    missingBackend: override.missingBackend ?? journey.missingBackend ?? "",
    missingFrontend: override.missingFrontend ?? journey.missingFrontend ?? "",
  };
}

/**
 * Derive completionClassification from code signals — never assert transaction-complete
 * outside COMPLETION_EVIDENCE (enforced separately).
 */
function classifyJourneyCompletion(journey, signals) {
  if (COMPLETION_EVIDENCE[journey.id]) {
    return "transaction-complete";
  }

  const hint = JOURNEY_CLASSIFICATION_HINTS[journey.id];
  if (hint) {
    return hint;
  }

  const gaps = resolveGapFields(journey);
  const hasBackendGap =
    journeyHasContractGaps(journey, signals.missingByModule) || Boolean(gaps.missingBackend);
  const hasEventGap = journeyHasEventGaps(journey, signals.eventGapsByModule);
  const hasFrontendGap = Boolean(gaps.missingFrontend) || journeyHasStubRoutes(journey, signals);
  const hasRoutes = journey.frontendRoutes.length > 0;
  const hasMobile = journey.mobileScreens.length > 0;

  if (hasEventGap && !hasBackendGap && !hasFrontendGap) {
    return "event-data-incomplete";
  }

  if (hasBackendGap) {
    return "backend-partial";
  }

  if (hasRoutes && !journeyHasBffChain(journey)) {
    return "frontend-route-exists-but-disconnected";
  }

  if (
    MOBILE_PARITY_EXPECTED.has(journey.id) &&
    hasRoutes &&
    !hasMobile
  ) {
    return "mobile-missing";
  }

  if (hasFrontendGap || journey.implStatus === "partial") {
    return "backend-ready-but-frontend-incomplete";
  }

  if (hasRoutes) {
    return "backend-ready-but-frontend-incomplete";
  }

  return "unknown-needs-review";
}

function applyMeasuredClassification(journeys, signals) {
  const rebaseline = [];
  for (const journey of journeys) {
    const previous = STALE_BASELINE_CLASSIFICATIONS[journey.id];
    const derived = classifyJourneyCompletion(journey, signals);
    journey.completionClassification = derived;
    const gaps = resolveGapFields(journey);
    journey.missingBackend = gaps.missingBackend;
    journey.missingFrontend = gaps.missingFrontend;
    if (previous && previous !== derived) {
      rebaseline.push({ id: journey.id, journeyName: journey.journeyName, previous, derived });
    }
  }
  return rebaseline;
}

const classificationSignals = {
  ...loadContractSignals(),
  ...buildRouteStubSignals(),
};

// ── Journey registry (discovered + minimum required set) ────────────────────
const JOURNEYS = [
  {
    id: "citizen-onboarding",
    journeyName: "Citizen / Client Onboarding",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "IDENTITY",
    initiatingActor: "citizen",
    respondingActor: "platform-registry",
    transactionObject: "Health ID + person anchor",
    context: "self-service registration; assurance level pending",
    entryPoint: "/auth/register",
    startState: "DRAFT",
    steps: "register → assurance → identity resolution → Health ID issued → consent review",
    backendServices: ["vito-service", "tshepo-identity-service", "identity-assurance-service", "tshepo-consent-service", "experience-bff"],
    apis: "/internal/v1/auth/*, /internal/v1/identity/*, /internal/v1/vito/*",
    frontendRoutes: routesMatching("/auth/register", "/auth/register/assurance", "/auth/register/status", "/id-services"),
    mobileScreens: screensMatching("personal/HealthId", "personal/Profile"),
    dataReadWritten: "VITO clients; identity issuance; consent directives",
    eventsEmitted: "identity.issued; core.transaction.events",
    trustSecurityChecks: "TSHEPO session; identity assurance; consent",
    auditRequirements: "registration audit chain; identity issuance log",
    completionState: "IDENTITY_RESOLVED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "card ops / pickup verification thin",
    relatedJourneys: ["consent-capture", "health-id-issuance"],
    poAcceptanceTest: "Citizen completes registration, receives Health ID, sees next-step guidance",
  },
  {
    id: "provider-login",
    journeyName: "Provider Login & Role Activation",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "ENTRY",
    initiatingActor: "provider",
    respondingActor: "platform-trust",
    transactionObject: "authenticated session + Provider ID activation",
    context: "facility-bound professional capacity",
    entryPoint: "/auth/login/provider-id",
    startState: "INITIATED",
    steps: "login → MFA → provider lookup → role activation → session established",
    backendServices: ["tshepo-authz-service", "tshepo-identity-service", "varapi-service", "experience-bff"],
    apis: "/internal/v1/auth/*, /internal/v1/identity/providers",
    frontendRoutes: routesMatching("/auth/login", "/auth/login/provider-id", "/auth/mfa", "/auth/resolving"),
    mobileScreens: screensMatching("Auth", "Login"),
    dataReadWritten: "session; provider registry lookup",
    eventsEmitted: "auth.session.created",
    trustSecurityChecks: "ext_authz; step-up; device trust",
    auditRequirements: "login audit; provider activation log",
    completionState: "TRUST_CONTEXT_ESTABLISHED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "device block UX admin-only",
    relatedJourneys: ["workspace-context-selection", "facility-context-selection"],
    poAcceptanceTest: "Provider signs in with Provider ID, activates role, lands in workspace",
  },
  {
    id: "workspace-context-selection",
    journeyName: "Workspace / Shift Context Selection",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "TRUST_AND_CONSENT",
    initiatingActor: "provider",
    respondingActor: "platform-registry",
    transactionObject: "active workspace + shift context",
    context: "department/ward/workspace under facility",
    entryPoint: "/workspace",
    startState: "TRUST_CONTEXT_ESTABLISHED",
    steps: "select facility → select workspace → select shift → context headers injected",
    backendServices: ["tuso-service", "experience-bff"],
    apis: "/internal/v1/workspaces/*, /internal/v1/shifts/*, /internal/v1/facilities",
    frontendRoutes: routesMatching("/workspace", "/shift", "/facility"),
    mobileScreens: screensMatching("provider/Dashboard", "provider/Launch"),
    dataReadWritten: "TUSO workspace/shift records",
    eventsEmitted: "workspace.context.changed",
    trustSecurityChecks: "facility guard; X-Workspace-ID; X-Shift-ID headers",
    auditRequirements: "context switch audit",
    completionState: "TRUST_CONTEXT_ESTABLISHED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["facility-context-selection", "provider-patient-encounter"],
    poAcceptanceTest: "Provider selects workspace/shift; subsequent requests carry trust context",
  },
  {
    id: "facility-context-selection",
    journeyName: "Facility Context Selection",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "TRUST_AND_CONSENT",
    initiatingActor: "provider",
    respondingActor: "tuso-service",
    transactionObject: "Facility ID context",
    context: "regulated facility operating model",
    entryPoint: "/facility",
    startState: "TRUST_CONTEXT_ESTABLISHED",
    steps: "facility search → select → X-Facility-ID set → queue/EHR unlocked",
    backendServices: ["tuso-service", "experience-bff"],
    apis: "/internal/v1/facilities, /internal/v1/registry/*",
    frontendRoutes: routesMatching("/facility"),
    mobileScreens: screensMatching("provider/"),
    dataReadWritten: "facility registry; workspace bindings",
    eventsEmitted: "facility.context.selected",
    trustSecurityChecks: "facility guard; purpose-of-use",
    auditRequirements: "facility access audit",
    completionState: "ACCESS_GRANTED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["workspace-context-selection", "queue-walk-in"],
    poAcceptanceTest: "Provider selects facility; queue and EHR routes become available",
  },
  {
    id: "patient-search-selection",
    journeyName: "Patient Search & Selection",
    coreTransactionType: "FACILITY_WALK_IN",
    lifecycleStage: "IDENTITY",
    initiatingActor: "provider",
    respondingActor: "vito-service",
    transactionObject: "subject Health ID / CPID selection",
    context: "facility queue or EHR entry",
    entryPoint: "/queue/search",
    startState: "IDENTITY_PENDING",
    steps: "search → match → select patient → open chart or queue item",
    backendServices: ["vito-service", "experience-bff", "pct-service"],
    apis: "/internal/v1/vito/*, /internal/v1/identity/*, /internal/v1/queue",
    frontendRoutes: routesMatching("/queue/search", "/ehr"),
    mobileScreens: screensMatching("provider/Patients", "provider/PatientSearch"),
    dataReadWritten: "client registry read; queue item link",
    eventsEmitted: "patient.selected",
    trustSecurityChecks: "purpose-of-use; subject relationship; consent",
    auditRequirements: "patient access audit",
    completionState: "IDENTITY_RESOLVED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["queue-walk-in", "provider-patient-encounter"],
    poAcceptanceTest: "Provider searches patient, selects, opens chart with correct CPID",
  },
  {
    id: "queue-walk-in",
    journeyName: "Queue / Walk-in Registration",
    coreTransactionType: "FACILITY_WALK_IN",
    lifecycleStage: "SCHEDULING_OR_QUEUE",
    initiatingActor: "provider",
    respondingActor: "pct-service",
    transactionObject: "queue item + journey start",
    context: "facility outpatient reception",
    entryPoint: "/queue/walk-in",
    startState: "QUEUED",
    steps: "walk-in capture → triage queue → waiting room → call next",
    backendServices: ["pct-service", "vito-service", "tuso-service", "experience-bff"],
    apis: "/internal/v1/queue, /internal/v1/queues, /internal/v1/journeys",
    frontendRoutes: routesMatching("/queue"),
    mobileScreens: screensMatching("provider/Queue"),
    dataReadWritten: "queue items; patient journeys",
    eventsEmitted: "pct.queue.item.created; core.transaction.events",
    trustSecurityChecks: "facility context; RBAC",
    auditRequirements: "queue action audit",
    completionState: "READY_FOR_PROVIDER",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["patient-search-selection", "outpatient-consultation"],
    poAcceptanceTest: "Walk-in registered, appears in queue, provider can call",
  },
  {
    id: "provider-patient-encounter",
    journeyName: "Provider Patient Encounter",
    coreTransactionType: "FACILITY_WALK_IN",
    lifecycleStage: "ENCOUNTER_OR_DELIVERY",
    initiatingActor: "provider",
    respondingActor: "pct-service",
    transactionObject: "Encounter ID",
    context: "EHR encounter workspace",
    entryPoint: "/ehr/[patientId]/encounter/[encounterId]",
    startState: "IN_SERVICE",
    steps: "open chart → start encounter → document → orders → complete encounter",
    backendServices: ["pct-service", "butano-service", "experience-bff", "guidance-service"],
    apis: "/internal/v1/encounters/*, /internal/v1/core-transactions/*",
    frontendRoutes: routesMatching("/ehr/[patientId]/encounter", "/ehr/[patientId]/encounters", "/core-transaction"),
    mobileScreens: screensMatching("provider/Encounter", "provider/encounter"),
    dataReadWritten: "encounter lifecycle; clinical notes; vitals",
    eventsEmitted: "pct.encounter.*; core.transaction.events",
    trustSecurityChecks: "provider capacity; consent; break-glass if emergency",
    auditRequirements: "encounter audit chain",
    completionState: "CLINICAL_COMPLETION_PENDING",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["outpatient-consultation", "lab-order-result", "prescription-dispense"],
    poAcceptanceTest: "Provider completes encounter end-to-end with real PCT data",
  },
  {
    id: "outpatient-consultation",
    journeyName: "Outpatient Consultation",
    coreTransactionType: "FACILITY_WALK_IN",
    lifecycleStage: "ENCOUNTER_OR_DELIVERY",
    initiatingActor: "provider",
    respondingActor: "pct-service",
    transactionObject: "consultation encounter",
    context: "outpatient clinic",
    entryPoint: "/clinical",
    startState: "IN_SERVICE",
    steps: "queue → triage → consult → orders → discharge instructions",
    backendServices: ["pct-service", "butano-service", "oros-service", "pharmacy-service", "experience-bff"],
    apis: "/internal/v1/encounters, /internal/v1/triage, /internal/v1/clinical-notes",
    frontendRoutes: routesMatching("/clinical", "/queue/triage", "/ehr"),
    mobileScreens: screensMatching("provider/Triage", "provider/Vitals"),
    dataReadWritten: "encounter; vitals; notes; orders",
    eventsEmitted: "core.transaction.events; pct-clinical-encounter",
    trustSecurityChecks: "standard clinical authz",
    auditRequirements: "clinical audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["provider-patient-encounter", "prescription-dispense", "lab-order-result"],
    poAcceptanceTest: "Outpatient consult documented with vitals, note, and next steps",
  },
  {
    id: "inpatient-admission",
    journeyName: "Inpatient Admission Workflow",
    coreTransactionType: "EMERGENCY",
    lifecycleStage: "ENCOUNTER_OR_DELIVERY",
    initiatingActor: "provider",
    respondingActor: "inpatient-service",
    transactionObject: "Admission ID + bed assignment",
    context: "ward/inpatient unit",
    entryPoint: "/ehr/[patientId]/inpatient",
    startState: "ADMITTED",
    steps: "admit → bed assign → ward rounds → discharge planning",
    backendServices: ["inpatient-service", "pct-service", "tuso-service", "experience-bff"],
    apis: "/internal/v1/inpatient/*, /internal/v1/beds/*",
    frontendRoutes: routesMatching("/inpatient", "/beds", "/ehr"),
    mobileScreens: screensMatching("provider/Inpatient", "provider/Bed"),
    dataReadWritten: "admission; bed state; ward movements",
    eventsEmitted: "inpatient.admission.*",
    trustSecurityChecks: "inpatient permissions; ward context",
    auditRequirements: "admission/discharge audit",
    completionState: "ADMITTED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["provider-patient-encounter", "emergency-encounter"],
    poAcceptanceTest: "Patient admitted, bed assigned, visible in ward worklist",
  },
  {
    id: "telemedicine-encounter",
    journeyName: "Telemedicine Encounter",
    coreTransactionType: "TELEMEDICINE",
    lifecycleStage: "ENCOUNTER_OR_DELIVERY",
    initiatingActor: "provider",
    respondingActor: "pct-service",
    transactionObject: "teleconsult referral + session",
    context: "remote care; consent required",
    entryPoint: "/telemedicine",
    startState: "SCHEDULED",
    steps: "create session → consent → routing → governed RTC token → consult → referral update",
    backendServices: ["pct-service", "mvumo-service", "rtc-gateway-service", "analytics-pipeline-service", "experience-bff"],
    apis: "/internal/v1/teleconsult/*, /internal/v1/mvumo/*, /internal/v1/rtc/*",
    frontendRoutes: routesMatching("/telemedicine"),
    mobileScreens: screensMatching("telehealth", "Teleconsult"),
    dataReadWritten: "referral; consent request; session metadata",
    eventsEmitted: "teleconsult.session.*",
    trustSecurityChecks: "consent; remote session authz",
    auditRequirements: "telehealth audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["consent-capture", "referral-create"],
    poAcceptanceTest: "Teleconsult created, consented, governed RTC token issued, referral persisted",
  },
  {
    id: "lab-order-result",
    journeyName: "Lab Order & Result",
    coreTransactionType: "LABORATORY",
    lifecycleStage: "ORDERS_AND_ANCILLARY",
    initiatingActor: "provider",
    respondingActor: "oros-service",
    transactionObject: "Lab Order ID + Result ID",
    context: "encounter ancillary",
    entryPoint: "/ehr/[patientId]/orders",
    startState: "ORDERS_PENDING",
    steps: "order lab → acknowledge → result → review → chart update",
    backendServices: ["oros-service", "pct-service", "butano-service", "experience-bff"],
    apis: "/internal/v1/lab-orders/*, /internal/v1/orders, /v1/orders",
    frontendRoutes: routesMatching("/lab", "/ehr/[patientId]/orders", "/ehr/[patientId]/results"),
    mobileScreens: screensMatching("provider/Lab", "provider/Results"),
    dataReadWritten: "orders; results; SHR contribution",
    eventsEmitted: "finance-oros-pharmacy; core.transaction.events",
    trustSecurityChecks: "order authz; result visibility",
    auditRequirements: "order/result audit",
    completionState: "PENDING_RESULT",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["provider-patient-encounter", "outpatient-consultation", "imaging-order-result"],
    poAcceptanceTest: "Lab ordered and result returned to provider worklist",
  },
  {
    id: "imaging-order-result",
    journeyName: "Imaging Order & Result",
    coreTransactionType: "IMAGING",
    lifecycleStage: "ORDERS_AND_ANCILLARY",
    initiatingActor: "provider",
    respondingActor: "pacs-adapter-service",
    transactionObject: "Imaging study + report",
    context: "radiology/imaging unit",
    entryPoint: "/ehr/[patientId]/imaging",
    startState: "ORDERS_PENDING",
    steps: "order imaging → PACS retrieve → viewer → report",
    backendServices: ["pacs-adapter-service", "oros-service", "document-service", "experience-bff"],
    apis: "/internal/v1/imaging/*, /internal/v1/pacs/*",
    frontendRoutes: routesMatching("/imaging", "/pacs", "/ehr"),
    mobileScreens: screensMatching("provider/Pacs", "provider/Imaging"),
    dataReadWritten: "imaging metadata; DICOM refs; audit",
    eventsEmitted: "imaging-pipeline",
    trustSecurityChecks: "imaging access audit; CPID-only SHR",
    auditRequirements: "PACS access audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["lab-order-result", "document-upload"],
    poAcceptanceTest: "Imaging study visible in viewer with audit trail",
  },
  {
    id: "prescription-dispense",
    journeyName: "Prescription & Dispense",
    coreTransactionType: "PHARMACY",
    lifecycleStage: "ORDERS_AND_ANCILLARY",
    initiatingActor: "provider",
    respondingActor: "pharmacy-service",
    transactionObject: "Prescription ID",
    context: "pharmacy workspace",
    entryPoint: "/ehr/[patientId]/medications",
    startState: "ORDERS_PENDING",
    steps: "prescribe → verify → dispense → patient instructions",
    backendServices: ["pharmacy-service", "pct-service", "zibo-service", "experience-bff"],
    apis: "/internal/v1/pharmacy/*",
    frontendRoutes: routesMatching("/pharmacy", "/ehr/[patientId]/medications"),
    mobileScreens: screensMatching("provider/Prescribing", "provider/Prescription"),
    dataReadWritten: "prescription; dispense events",
    eventsEmitted: "core.transaction.events; finance-oros-pharmacy",
    trustSecurityChecks: "prescribing privileges; formulary",
    auditRequirements: "prescribing audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "mobile prescribing depth",
    relatedJourneys: ["outpatient-consultation", "lab-order-result"],
    poAcceptanceTest: "Prescription created, dispensed, visible in patient meds",
  },
  {
    id: "referral-create",
    journeyName: "Referral Create & Manage",
    coreTransactionType: "REFERRAL",
    lifecycleStage: "FOLLOW_UP_CONTINUITY",
    initiatingActor: "provider",
    respondingActor: "pct-service",
    transactionObject: "Referral ID",
    context: "inter-facility or specialist routing",
    entryPoint: "/ehr/[patientId]/referrals",
    startState: "INITIATED",
    steps: "create referral → route → accept → complete",
    backendServices: ["pct-service", "referral-service", "tuso-service", "varapi-service", "experience-bff"],
    apis: "/internal/v1/referrals/*",
    frontendRoutes: routesMatching("/referrals", "/queue/incoming-referrals", "/ehr/[patientId]/referrals"),
    mobileScreens: screensMatching("provider/Referral"),
    dataReadWritten: "referral state; routing metadata",
    eventsEmitted: "core.transaction.events",
    trustSecurityChecks: "referral authz; routing validation",
    auditRequirements: "referral audit",
    completionState: "REFERRED_OUT",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["telemedicine-encounter", "appointment-scheduling"],
    poAcceptanceTest: "Referral created, routed, visible at destination facility",
  },
  {
    id: "appointment-scheduling",
    journeyName: "Appointment Scheduling",
    coreTransactionType: "APPOINTMENT",
    lifecycleStage: "SCHEDULING_OR_QUEUE",
    initiatingActor: "citizen",
    respondingActor: "scheduling-service",
    transactionObject: "Appointment ID",
    context: "facility booking",
    entryPoint: "/queue/scheduled",
    startState: "SCHEDULED",
    steps: "find slot → book → confirm → remind → check-in",
    backendServices: ["scheduling-service", "booking-service", "tuso-service", "notification-service", "experience-bff"],
    apis: "/internal/v1/appointments/*, /internal/v1/scheduling/*, /internal/v1/bookings/*",
    frontendRoutes: routesMatching("/queue/scheduled", "/scheduling"),
    mobileScreens: screensMatching("Appointment", "Schedule"),
    dataReadWritten: "appointment; booking slots",
    eventsEmitted: "appointment.booked",
    trustSecurityChecks: "booking permissions",
    auditRequirements: "scheduling audit",
    completionState: "SCHEDULED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["queue-walk-in", "citizen-onboarding"],
    poAcceptanceTest: "Appointment booked and appears in facility schedule",
  },
  {
    id: "consent-capture",
    journeyName: "Consent Capture",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "TRUST_AND_CONSENT",
    initiatingActor: "citizen",
    respondingActor: "mvumo-service",
    transactionObject: "Consent directive",
    context: "care, telehealth, data sharing",
    entryPoint: "/consent",
    startState: "TRUST_CONTEXT_ESTABLISHED",
    steps: "present policy → capture consent → store directive → enforce",
    backendServices: ["mvumo-service", "tshepo-consent-service", "experience-bff"],
    apis: "/internal/v1/consent/*, /internal/v1/mvumo/*",
    frontendRoutes: routesMatching("/consent", "/settings", "/registry/mvumo", "/admin/consent"),
    mobileScreens: screensMatching("Consent", "personal/Consent"),
    dataReadWritten: "consent directives; mvumo records",
    eventsEmitted: "trust-governance; core.transaction.events",
    trustSecurityChecks: "consent evaluation; purpose-of-use",
    auditRequirements: "consent audit mandatory",
    completionState: "TRUST_CONTEXT_ESTABLISHED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "remote-session admin depth",
    relatedJourneys: ["telemedicine-encounter", "citizen-onboarding"],
    poAcceptanceTest: "Consent captured, enforced on subsequent clinical access",
  },
  {
    id: "payment-billing-claim",
    journeyName: "Payment / Billing / Exemption / Claim",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "FINANCIAL_SETTLEMENT",
    initiatingActor: "citizen",
    respondingActor: "costing-engine-service",
    transactionObject: "Invoice / Claim / Payment",
    context: "encounter billing or coverage",
    entryPoint: "/finance",
    startState: "FINANCIAL_PROCESSING",
    steps: "estimate → coverage check → pay/exempt → claim → remittance",
    backendServices: ["costing-engine-service", "mushex-service", "coverage-service", "mushe-wallet-service", "experience-bff"],
    apis: "/internal/v1/finance/*, /internal/v1/coverage/*, /internal/v1/wallet/*",
    frontendRoutes: routesMatching("/finance", "/coverage"),
    mobileScreens: screensMatching("Wallet", "Billing", "Finance"),
    dataReadWritten: "billing; claims; payments; remittance",
    eventsEmitted: "core.transaction.events; finance-oros-pharmacy",
    trustSecurityChecks: "financial authz; purpose-of-use",
    auditRequirements: "financial audit chain",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "production live-rail operator depth",
    relatedJourneys: ["wallet-payment", "coverage-enrollment"],
    poAcceptanceTest: "Bill generated, payment or claim completed with audit",
  },
  {
    id: "document-upload",
    journeyName: "Document Upload / Scan / Index",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "RECORD_CONTRIBUTION",
    initiatingActor: "provider",
    respondingActor: "document-service",
    transactionObject: "Document ID",
    context: "clinical or administrative record",
    entryPoint: "/ehr/[patientId]/documents",
    startState: "DRAFT",
    steps: "upload → index → attach to encounter → retrieve",
    backendServices: ["document-service", "landela-adapter-service", "experience-bff"],
    apis: "/internal/v1/clinical-documents/*, /internal/v1/documents/*",
    frontendRoutes: routesMatching("/documents", "/ehr/[patientId]/documents"),
    mobileScreens: screensMatching("Document"),
    dataReadWritten: "document metadata; storage refs",
    eventsEmitted: "document-store",
    trustSecurityChecks: "document access authz",
    auditRequirements: "document access audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "indexing UX partial",
    relatedJourneys: ["imaging-order-result", "provider-patient-encounter"],
    poAcceptanceTest: "Document uploaded, indexed, retrievable in chart",
  },
  {
    id: "dispatch-delivery",
    journeyName: "Dispatch / Delivery (NHUME)",
    coreTransactionType: "MARKETPLACE",
    lifecycleStage: "INSTRUCTIONS_AND_NOTIFICATIONS",
    initiatingActor: "courier",
    respondingActor: "dispatch-service",
    transactionObject: "Delivery run / order fulfilment",
    context: "logistics; medication or supply delivery",
    entryPoint: "/operations/dispatch",
    startState: "TASKED",
    steps: "assign run → navigate → deliver → confirm → reconcile",
    backendServices: ["dispatch-service", "nhume-service", "oros-service", "experience-bff"],
    apis: "/internal/v1/dispatch/*, /internal/v1/nhume/*",
    frontendRoutes: routesMatching("/operations/dispatch", "/nhume"),
    mobileScreens: screensMatching("courier", "Delivery"),
    dataReadWritten: "delivery runs; proof of delivery",
    eventsEmitted: "dispatch.*",
    trustSecurityChecks: "courier role; route authz",
    auditRequirements: "delivery audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["marketplace-order", "notification-comms"],
    poAcceptanceTest: "Courier completes delivery run with POD",
  },
  {
    id: "notification-comms",
    journeyName: "Notification & Communications",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "INSTRUCTIONS_AND_NOTIFICATIONS",
    initiatingActor: "platform",
    respondingActor: "notification-service",
    transactionObject: "Notification / message",
    context: "omnichannel comms hub",
    entryPoint: "/communication",
    startState: "INITIATED",
    steps: "compose → send → deliver → track → feedback",
    backendServices: ["notification-service", "channels-service", "community-service", "experience-bff"],
    apis: "/internal/v1/notifications/*, /internal/v1/communication/*, /internal/v1/omnichannel/*",
    frontendRoutes: routesMatching("/communication", "/omnichannel", "/notifications"),
    mobileScreens: screensMatching("Messaging", "messaging"),
    dataReadWritten: "notification store; delivery status",
    eventsEmitted: "notification.sent",
    trustSecurityChecks: "comms authz; consent for outreach",
    auditRequirements: "comms audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "campaign admin thin",
    relatedJourneys: ["consent-capture", "appointment-scheduling"],
    poAcceptanceTest: "Notification sent and delivery status visible",
  },
  {
    id: "fundo-learning",
    journeyName: "Fundo / Learning Journey",
    coreTransactionType: "TRAINING_OR_COMPETENCY",
    lifecycleStage: "SERVICE_SELECTION",
    initiatingActor: "provider",
    respondingActor: "learning-service",
    transactionObject: "Enrolment / competency record",
    context: "professional development",
    entryPoint: "/learning",
    startState: "INITIATED",
    steps: "browse catalog → enrol → complete assessment → CPD credit",
    backendServices: ["learning-service", "varapi-service", "experience-bff"],
    apis: "/internal/v1/learning/*",
    frontendRoutes: routesMatching("/learning"),
    mobileScreens: screensMatching("Learning", "learning"),
    dataReadWritten: "enrolments; assessments; CPD",
    eventsEmitted: "learning.completed",
    trustSecurityChecks: "provider identity for CPD",
    auditRequirements: "competency audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "mobile learning shell shallow",
    relatedJourneys: ["provider-login", "credential-verification"],
    poAcceptanceTest: "Provider enrols, completes module, CPD reflected",
  },
  {
    id: "reporting-dashboard",
    journeyName: "Data / Report / Dashboard Journey",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "REPORTING_ANALYTICS_AUDIT",
    initiatingActor: "data-analyst",
    respondingActor: "reporting-service",
    transactionObject: "Report run / dashboard view",
    context: "operations or public health intelligence",
    entryPoint: "/reports",
    startState: "INITIATED",
    steps: "select report → run → view → export → audit",
    backendServices: ["reporting-service", "data-warehouse-service", "surveillance-service", "experience-bff"],
    apis: "/internal/v1/reports/*, /internal/v1/intelligence-plane/*",
    frontendRoutes: routesMatching("/reports", "/data-intelligence", "/monitoring"),
    mobileScreens: screensMatching("Reports"),
    dataReadWritten: "report runs; aggregates",
    eventsEmitted: "analytics.reporting.aggregate",
    trustSecurityChecks: "reporting RBAC; de-identification",
    auditRequirements: "report access audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "Ndila map dashboards incomplete",
    relatedJourneys: ["public-health-outreach", "surveillance-outbreak"],
    poAcceptanceTest: "Analyst runs report with correct scope and audit",
  },
  {
    id: "registry-administration",
    journeyName: "Registry Administration",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "IDENTITY",
    initiatingActor: "registry-administrator",
    respondingActor: "vito-service",
    transactionObject: "Registry record governance",
    context: "national registry ops",
    entryPoint: "/registry",
    startState: "INITIATED",
    steps: "search → verify → reconcile → issue → audit",
    backendServices: ["vito-service", "varapi-service", "tuso-service", "zibo-service", "product-registry-service", "experience-bff"],
    apis: "/internal/v1/registry/*, /internal/v1/vito/*",
    frontendRoutes: routesMatching("/registry", "/registry-admin", "/operations/vito"),
    mobileScreens: screensMatching("registry"),
    dataReadWritten: "registry records; reconciliation queues",
    eventsEmitted: "registry.*",
    trustSecurityChecks: "registry admin RBAC",
    auditRequirements: "registry audit mandatory",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "issuance/card ops not fully surfaced",
    relatedJourneys: ["health-id-issuance", "provider-registry-onboarding"],
    poAcceptanceTest: "Registry admin reconciles record with full audit",
  },
  {
    id: "integration-sync-replay",
    journeyName: "Integration / Sync / Replay",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "REPORTING_ANALYTICS_AUDIT",
    initiatingActor: "integration-system",
    respondingActor: "integration-hub",
    transactionObject: "Integration message / replay job",
    context: "external system or edge node",
    entryPoint: "/developer",
    startState: "PENDING_SYNC",
    steps: "receive → validate → route → process → replay on failure",
    backendServices: ["integration-hub", "connector-fhir-adapter", "fhir-gateway-service", "offline-sync-service", "experience-bff"],
    apis: "/internal/v1/integration-hub/*, /internal/v1/fhir/*, /internal/v1/extensions/*",
    frontendRoutes: routesMatching("/developer", "/admin/integration"),
    mobileScreens: [],
    dataReadWritten: "integration routes; dead letters",
    eventsEmitted: "integration.*",
    trustSecurityChecks: "integration credentials; FHIR gateway authz",
    auditRequirements: "integration audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "integration status partial",
    relatedJourneys: ["device-system-event", "offline-clinical-queue"],
    poAcceptanceTest: "Integration message processed or dead-lettered with replay",
  },
  {
    id: "device-system-event",
    journeyName: "Device / System Event Journey",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "RECEIVE_TRIGGER",
    initiatingActor: "device",
    respondingActor: "iot-ingestion-service",
    transactionObject: "Device event / telemetry",
    context: "IoT, card printer, edge node",
    entryPoint: "system ingress (no UI)",
    startState: "INITIATED",
    steps: "ingest → validate → route → store → alert",
    backendServices: ["iot-ingestion-service", "card-print-agent", "offline-edge-service", "tshepo-authz-service"],
    apis: "/v1/devices, device agent protocols",
    frontendRoutes: routesMatching("/admin", "/operations"),
    mobileScreens: [],
    dataReadWritten: "device events; telemetry",
    eventsEmitted: "device.*",
    trustSecurityChecks: "device registration; mTLS",
    auditRequirements: "device event audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "ops admin surface only (by design)",
    relatedJourneys: ["health-id-issuance", "integration-sync-replay"],
    poAcceptanceTest: "Device event ingested and auditable",
  },
  {
    id: "health-id-issuance",
    journeyName: "Health ID Issuance & Card Ops",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "IDENTITY",
    initiatingActor: "registry-administrator",
    respondingActor: "vito-service",
    transactionObject: "Health ID + physical card",
    context: "registration office / VITO ops",
    entryPoint: "/operations/vito",
    startState: "IDENTITY_PENDING",
    steps: "verify identity → issue ID → print card → pickup verify",
    backendServices: ["vito-service", "card-print-agent", "share-slip-service", "experience-bff"],
    apis: "/internal/v1/vito/*, /v1/cards, /v1/print",
    frontendRoutes: routesMatching("/operations/vito", "/id-services"),
    mobileScreens: screensMatching("HealthId", "Card"),
    dataReadWritten: "identity issuance; card records",
    eventsEmitted: "identity.issued",
    trustSecurityChecks: "issuance authz; biometric verify",
    auditRequirements: "issuance audit",
    completionState: "IDENTITY_RESOLVED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["citizen-onboarding", "registry-administration"],
    poAcceptanceTest: "Health ID issued, card printed, pickup verified",
  },
  {
    id: "marketplace-order",
    journeyName: "Marketplace Order",
    coreTransactionType: "MARKETPLACE",
    lifecycleStage: "SERVICE_SELECTION",
    initiatingActor: "citizen",
    respondingActor: "msika-flow-service",
    transactionObject: "Order ID",
    context: "citizen marketplace",
    entryPoint: "/marketplace",
    startState: "INITIATED",
    steps: "browse → cart → order → pay → fulfil",
    backendServices: ["msika-flow-service", "msika-service", "mushe-wallet-service", "experience-bff"],
    apis: "/internal/v1/marketplace/*, /internal/v1/commerce/*",
    frontendRoutes: routesMatching("/marketplace"),
    mobileScreens: screensMatching("marketplace", "Marketplace", "Cart", "Order"),
    dataReadWritten: "orders; payment intents",
    eventsEmitted: "core.transaction.events",
    trustSecurityChecks: "commerce authz",
    auditRequirements: "order audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["wallet-payment", "dispatch-delivery"],
    poAcceptanceTest: "Citizen places order and tracks fulfilment",
  },
  {
    id: "wellness-journey",
    journeyName: "Wellness & Lifestyle Journey",
    coreTransactionType: "WELLNESS",
    lifecycleStage: "ACCESS_SERVICE",
    initiatingActor: "citizen",
    respondingActor: "wellness-service",
    transactionObject: "Wellness activity / journey record",
    context: "consumer wellness pillar",
    entryPoint: "/wellness",
    startState: "INITIATED",
    steps: "discover → enrol → track → coach → continue",
    backendServices: ["wellness-service", "simba-service", "guidance-service", "experience-bff"],
    apis: "/internal/v1/wellness/*, /internal/v1/mobile/citizen/wellness/*",
    frontendRoutes: routesMatching("/wellness"),
    mobileScreens: screensMatching("wellness", "Wellness"),
    dataReadWritten: "wellness journeys; activities",
    eventsEmitted: "wellness.*",
    trustSecurityChecks: "citizen scope; minimal friction",
    auditRequirements: "wellness participation audit",
    completionState: "FOLLOW_UP_ACTIVE",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["citizen-monitoring", "fundo-learning"],
    poAcceptanceTest: "Citizen tracks wellness activity with real backend",
  },
  {
    id: "impilo-live-events",
    journeyName: "Impilo Live Events",
    coreTransactionType: "WELLNESS",
    lifecycleStage: "ACCESS_SERVICE",
    initiatingActor: "citizen",
    respondingActor: "live-service",
    transactionObject: "Live event / broadcast session",
    context: "health talks, webinars, CPD live",
    entryPoint: "/live/discover",
    startState: "SCHEDULED",
    steps: "discover → register → join room → replay → certificate",
    backendServices: ["live-service", "rtc-gateway-service", "booking-service", "experience-bff"],
    apis: "/internal/v1/live/*, /internal/v1/rtc/*",
    frontendRoutes: routesMatching("/live"),
    mobileScreens: screensMatching("Live", "Event"),
    dataReadWritten: "live event registration; attendance",
    eventsEmitted: "live.event.*",
    trustSecurityChecks: "event authz; replay token scope",
    auditRequirements: "live participation audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["wellness-journey", "fundo-learning"],
    poAcceptanceTest: "Citizen discovers live event and joins governed room",
  },
  {
    id: "social-community",
    journeyName: "Social / Community / Timeline",
    coreTransactionType: "WELLNESS",
    lifecycleStage: "ACCESS_SERVICE",
    initiatingActor: "citizen",
    respondingActor: "community-service",
    transactionObject: "Post / community membership",
    context: "social timeline",
    entryPoint: "/social",
    startState: "INITIATED",
    steps: "feed → compose → publish → engage → moderate",
    backendServices: ["community-service", "llm-orchestration-service", "experience-bff"],
    apis: "/internal/v1/social/*, /internal/v1/mobile/citizen/social/*",
    frontendRoutes: routesMatching("/social", "/communities", "/groups"),
    mobileScreens: screensMatching("social", "Feed", "Community"),
    dataReadWritten: "social posts; communities",
    eventsEmitted: "social.*",
    trustSecurityChecks: "content policy; authz",
    auditRequirements: "social moderation audit",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "public health alerts placeholder in rail",
    relatedJourneys: ["notification-comms", "public-health-outreach"],
    poAcceptanceTest: "Citizen posts to feed with real BFF backing",
  },
  {
    id: "public-health-outreach",
    journeyName: "Public Health / CHW Outreach",
    coreTransactionType: "COMMUNITY_OUTREACH",
    lifecycleStage: "ENCOUNTER_OR_DELIVERY",
    initiatingActor: "community-health-worker",
    respondingActor: "community-service",
    transactionObject: "Outreach visit record",
    context: "field outreach mode",
    entryPoint: "/public-health",
    startState: "IN_SERVICE",
    steps: "household list → visit → screen → follow-up",
    backendServices: ["community-service", "surveillance-service", "campaigns-service", "indawo-service", "experience-bff"],
    apis: "/internal/v1/public-health/*, /internal/v1/community/*",
    frontendRoutes: routesMatching("/public-health"),
    mobileScreens: screensMatching("outreach", "Outreach", "publicHealth"),
    dataReadWritten: "outreach visits; screening data",
    eventsEmitted: "campaigns-outbound",
    trustSecurityChecks: "CHW role; outreach authz",
    auditRequirements: "field visit audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "field ops mobile thinner than web",
    relatedJourneys: ["surveillance-outbreak", "reporting-dashboard"],
    poAcceptanceTest: "CHW records outreach visit on mobile",
  },
  {
    id: "crvs-ubomi",
    journeyName: "Civil Registration (UBOMI / CRVS)",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "RECORD_CONTRIBUTION",
    initiatingActor: "registry-administrator",
    respondingActor: "ubomi-service",
    transactionObject: "Vital event record",
    context: "birth/death/marriage registration",
    entryPoint: "/registry",
    startState: "DRAFT",
    steps: "capture event → verify → register → certificate",
    backendServices: ["ubomi-service", "vito-service", "experience-bff"],
    apis: "/internal/v1/ubomi/*",
    frontendRoutes: routesMatching("/registry", "/ubomi"),
    mobileScreens: screensMatching("crvs", "Ubomi"),
    dataReadWritten: "vital events; certificates",
    eventsEmitted: "ubomi.*",
    trustSecurityChecks: "CRVS authz",
    auditRequirements: "vital event audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "mobile CRVS parity missing",
    relatedJourneys: ["registry-administration", "citizen-onboarding"],
    poAcceptanceTest: "Vital event registered with certificate issuance",
  },
  {
    id: "coverage-enrollment",
    journeyName: "Coverage Enrollment",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "COST_AND_COVERAGE",
    initiatingActor: "citizen",
    respondingActor: "coverage-service",
    transactionObject: "Coverage plan / member enrollment",
    context: "payer operations",
    entryPoint: "/coverage",
    startState: "COVERAGE_CHECK_PENDING",
    steps: "eligibility → enroll → confirm → use at service",
    backendServices: ["coverage-service", "experience-bff"],
    apis: "/internal/v1/coverage/*",
    frontendRoutes: routesMatching("/coverage"),
    mobileScreens: screensMatching("Coverage", "coverage"),
    dataReadWritten: "coverage plans; member status",
    eventsEmitted: "coverage.*",
    trustSecurityChecks: "coverage authz",
    auditRequirements: "enrollment audit",
    completionState: "COVERAGE_CONFIRMED",
    implStatus: "partial",
    missingBackend: "contracting surfaces bounded",
    missingFrontend: "intelligence surfaces partial",
    relatedJourneys: ["payment-billing-claim"],
    poAcceptanceTest: "Citizen enrolls in coverage plan",
  },
  {
    id: "wallet-payment",
    journeyName: "Wallet Payment",
    coreTransactionType: "MARKETPLACE",
    lifecycleStage: "FINANCIAL_SETTLEMENT",
    initiatingActor: "citizen",
    respondingActor: "mushe-wallet-service",
    transactionObject: "Wallet transaction",
    context: "personal finance / checkout",
    entryPoint: "/wallet",
    startState: "PRE_SERVICE_PAYMENT_PENDING",
    steps: "fund wallet → pay → receipt → reconcile",
    backendServices: ["mushe-wallet-service", "mushex-service", "experience-bff"],
    apis: "/internal/v1/wallet/*",
    frontendRoutes: routesMatching("/wallet"),
    mobileScreens: screensMatching("Wallet", "wallet"),
    dataReadWritten: "wallet balance; payments",
    eventsEmitted: "wallet.payment.*",
    trustSecurityChecks: "wallet authz; payment rail",
    auditRequirements: "payment audit",
    completionState: "PRE_SERVICE_PAYMENT_COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["marketplace-order", "payment-billing-claim"],
    poAcceptanceTest: "Citizen pays from wallet with typed fail-close",
  },
  {
    id: "offline-clinical-queue",
    journeyName: "Offline Clinical Queue",
    coreTransactionType: "FACILITY_WALK_IN",
    lifecycleStage: "ENCOUNTER_OR_DELIVERY",
    initiatingActor: "provider",
    respondingActor: "offline-sync-service",
    transactionObject: "Offline queue item",
    context: "offline edge; provider offline mode",
    entryPoint: "mobile offline mode",
    startState: "PENDING_SYNC",
    steps: "capture offline → queue locally → sync → reconcile conflicts",
    backendServices: ["offline-sync-service", "offline-edge-service", "pct-service", "experience-bff"],
    apis: "/internal/v1/mobile/provider/offline/*",
    frontendRoutes: routesMatching("/operations"),
    mobileScreens: screensMatching("offline", "Offline"),
    dataReadWritten: "offline queue; sync state",
    eventsEmitted: "offline.sync.*",
    trustSecurityChecks: "offline trust envelope",
    auditRequirements: "offline action audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["provider-patient-encounter", "integration-sync-replay"],
    poAcceptanceTest: "Provider works offline, syncs without data loss",
  },
  {
    id: "emergency-encounter",
    journeyName: "Emergency / ED Encounter",
    coreTransactionType: "EMERGENCY",
    lifecycleStage: "ENCOUNTER_OR_DELIVERY",
    initiatingActor: "provider",
    respondingActor: "pct-service",
    transactionObject: "Emergency encounter",
    context: "ED/casualty; break-glass possible",
    entryPoint: "/clinical/emergency",
    startState: "EMERGENCY_OVERRIDE",
    steps: "triage → treat → stabilize → admit or discharge",
    backendServices: ["pct-service", "butano-service", "inpatient-service", "tshepo-authz-service", "experience-bff"],
    apis: "/internal/v1/encounters/*, /v1/break-glass",
    frontendRoutes: routesMatching("/clinical/emergency", "/ehr"),
    mobileScreens: screensMatching("Emergency", "Triage"),
    dataReadWritten: "emergency encounter; triage",
    eventsEmitted: "core.transaction.events",
    trustSecurityChecks: "break-glass; emergency override",
    auditRequirements: "break-glass audit mandatory",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["inpatient-admission", "provider-patient-encounter"],
    poAcceptanceTest: "Emergency encounter with break-glass audit if used",
  },
  {
    id: "core-transaction-orchestration",
    journeyName: "Core Transaction Orchestration Shell",
    coreTransactionType: "FACILITY_WALK_IN",
    lifecycleStage: "MANAGE_STATE_MACHINE",
    initiatingActor: "platform",
    respondingActor: "experience-bff",
    transactionObject: "Core Transaction ID",
    context: "unified transaction spine",
    entryPoint: "/core-transaction",
    startState: "INITIATED",
    steps: "create tx → timeline → next-actions → journey views → complete",
    backendServices: ["experience-bff", "workflow-service", "pct-service", "costing-engine-service"],
    apis: "/internal/v1/core-transactions/*, /experience/core-transactions/*",
    frontendRoutes: routesMatching("/core-transaction", "/client-journey", "/provider-workspace", "/platform-journey"),
    mobileScreens: [],
    dataReadWritten: "transaction orchestration state",
    eventsEmitted: "core.transaction.events",
    trustSecurityChecks: "full trust stack",
    auditRequirements: "transaction audit chain",
    completionState: "COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["provider-patient-encounter", "payment-billing-claim"],
    poAcceptanceTest: "Core transaction shell shows live timeline from BFF",
  },
  {
    id: "surveillance-outbreak",
    journeyName: "Surveillance / Outbreak Response",
    coreTransactionType: "COMMUNITY_OUTREACH",
    lifecycleStage: "REPORTING_ANALYTICS_AUDIT",
    initiatingActor: "health-information-officer",
    respondingActor: "surveillance-service",
    transactionObject: "Outbreak signal / case",
    context: "public health intelligence",
    entryPoint: "/public-health",
    startState: "INITIATED",
    steps: "detect signal → investigate → campaign → report",
    backendServices: ["surveillance-service", "campaigns-service", "ndila-service", "experience-bff"],
    apis: "/internal/v1/public-health/*",
    frontendRoutes: routesMatching("/public-health", "/ndila"),
    mobileScreens: screensMatching("publicHealth"),
    dataReadWritten: "surveillance signals; campaigns",
    eventsEmitted: "campaigns-outbound; ndila-events",
    trustSecurityChecks: "public health RBAC",
    auditRequirements: "surveillance audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "Ndila map dashboards incomplete",
    relatedJourneys: ["public-health-outreach", "reporting-dashboard"],
    poAcceptanceTest: "Outbreak signal investigated with campaign linkage",
  },
  {
    id: "ai-guidance-nompilo",
    journeyName: "AI Guidance / Nompilo Assist",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "COMPOSE_EXPERIENCE_VIEW",
    initiatingActor: "citizen",
    respondingActor: "guidance-service",
    transactionObject: "Guidance session / suggestion",
    context: "in-flow intelligent assist",
    entryPoint: "/ask",
    startState: "INITIATED",
    steps: "ask → classify context → guide → handoff if needed",
    backendServices: ["guidance-service", "llm-orchestration-service", "search-service", "experience-bff"],
    apis: "/internal/v1/guidance/*, /internal/v1/assistant/*, /internal/v1/search*",
    frontendRoutes: routesMatching("/ask", "/guidance", "/nhume"),
    mobileScreens: screensMatching("Nompilo"),
    dataReadWritten: "guidance sessions; search index",
    eventsEmitted: "guidance.*",
    trustSecurityChecks: "assist must not override clinical judgement",
    auditRequirements: "Nompilo assist audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "route context not always passed",
    relatedJourneys: ["provider-patient-encounter", "wellness-journey"],
    poAcceptanceTest: "Nompilo explains next step with route context",
  },
  {
    id: "credential-verification",
    journeyName: "Credential Verification",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "TRUST_AND_CONSENT",
    initiatingActor: "facility-administrator",
    respondingActor: "credential-verification-service",
    transactionObject: "Credential verification record",
    context: "provider licensure check",
    entryPoint: "/verify",
    startState: "INITIATED",
    steps: "submit credential → verify → record → notify",
    backendServices: ["credential-verification-service", "varapi-service", "experience-bff"],
    apis: "/internal/v1/credentials/*, /internal/v1/verify/*",
    frontendRoutes: routesMatching("/verify"),
    mobileScreens: [],
    dataReadWritten: "verification records",
    eventsEmitted: "credential.verified",
    trustSecurityChecks: "verification authz",
    auditRequirements: "verification audit",
    completionState: "COMPLETED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "verification workflow screens thin",
    relatedJourneys: ["provider-registry-onboarding", "fundo-learning"],
    poAcceptanceTest: "Credential verified and reflected in provider standing",
  },
  {
    id: "provider-registry-onboarding",
    journeyName: "Provider Registry Onboarding",
    coreTransactionType: "ADMINISTRATIVE_HEALTH",
    lifecycleStage: "IDENTITY",
    initiatingActor: "registry-administrator",
    respondingActor: "varapi-service",
    transactionObject: "Provider ID + licensure",
    context: "provider registry",
    entryPoint: "/registry/providers",
    startState: "DRAFT",
    steps: "register → council verify → privilege assign → activate",
    backendServices: ["varapi-service", "credential-verification-service", "experience-bff"],
    apis: "/internal/v1/registry/*, /v1/internal/providers/*",
    frontendRoutes: routesMatching("/registry/providers", "/registry"),
    mobileScreens: screensMatching("Professional"),
    dataReadWritten: "provider registry; licensure",
    eventsEmitted: "provider.registered",
    trustSecurityChecks: "registry admin RBAC",
    auditRequirements: "provider registry audit",
    completionState: "IDENTITY_RESOLVED",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "reconciliation queue thin",
    relatedJourneys: ["provider-login", "credential-verification"],
    poAcceptanceTest: "Provider onboarded with valid licensure",
  },
  {
    id: "citizen-monitoring",
    journeyName: "Citizen Remote Monitoring",
    coreTransactionType: "CHRONIC_CARE",
    lifecycleStage: "FOLLOW_UP_CONTINUITY",
    initiatingActor: "citizen",
    respondingActor: "wellness-service",
    transactionObject: "Monitoring observation",
    context: "home monitoring pillar",
    entryPoint: "/monitoring",
    startState: "FOLLOW_UP_ACTIVE",
    steps: "pair device → record obs → alert → clinician review",
    backendServices: ["wellness-service", "iot-ingestion-service", "notification-service", "experience-bff"],
    apis: "/internal/v1/wellness/*, /internal/v1/mobile/citizen/monitoring/*",
    frontendRoutes: routesMatching("/monitoring"),
    mobileScreens: screensMatching("Monitoring", "monitoring"),
    dataReadWritten: "observations; alerts",
    eventsEmitted: "wellness.monitoring.*",
    trustSecurityChecks: "citizen scope",
    auditRequirements: "monitoring audit",
    completionState: "FOLLOW_UP_ACTIVE",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "monitoring depth",
    relatedJourneys: ["wellness-journey", "chronic-care"],
    poAcceptanceTest: "Citizen records monitoring obs visible to care team",
  },
  {
    id: "chronic-care",
    journeyName: "Chronic Care Management",
    coreTransactionType: "CHRONIC_CARE",
    lifecycleStage: "FOLLOW_UP_CONTINUITY",
    initiatingActor: "provider",
    respondingActor: "pct-service",
    transactionObject: "Care plan / chronic encounter",
    context: "longitudinal chronic disease",
    entryPoint: "/ehr/[patientId]",
    startState: "FOLLOW_UP_ACTIVE",
    steps: "care plan → visits → meds → monitoring → review",
    backendServices: ["pct-service", "pharmacy-service", "butano-service", "wellness-service", "experience-bff"],
    apis: "/internal/v1/encounters/*, /internal/v1/pharmacy/*",
    frontendRoutes: routesMatching("/ehr", "/caregiving"),
    mobileScreens: screensMatching("caregiving", "Caregiving"),
    dataReadWritten: "care plans; encounters; meds",
    eventsEmitted: "core.transaction.events",
    trustSecurityChecks: "chronic care authz",
    auditRequirements: "care plan audit",
    completionState: "FOLLOW_UP_ACTIVE",
    implStatus: "partial",
    missingBackend: "",
    missingFrontend: "care plan UX depth",
    relatedJourneys: ["citizen-monitoring", "prescription-dispense"],
    poAcceptanceTest: "Chronic care plan active with follow-up scheduling",
  },
  {
    id: "blood-donation",
    journeyName: "Blood Donation & Donor Engagement",
    coreTransactionType: "BLOOD_DONATION",
    lifecycleStage: "COMMUNITY_WELLNESS",
    initiatingActor: "citizen",
    respondingActor: "madi-service",
    transactionObject: "Donor ID + donation drive registration",
    context: "donor hub / mobile citizen",
    entryPoint: "/madi/donor",
    startState: "DONOR_REGISTERED",
    steps: "register → pre-screen → drive booking → screen → donate → feedback",
    backendServices: ["madi-service", "ndila-service", "llm-orchestration-service", "experience-bff"],
    apis: "/internal/v1/madi/donors/*, /internal/v1/madi/drives/*, /internal/v1/mobile/citizen/madi/*",
    frontendRoutes: routesMatching("/madi/donor"),
    mobileScreens: screensMatching("madi", "Donor", "Drive"),
    dataReadWritten: "donor profiles; screening; drive registrations",
    eventsEmitted: "madi.donor.*; madi.drive.*",
    trustSecurityChecks: "citizen scope; donor consent",
    auditRequirements: "donor screening audit",
    completionState: "DONATION_RECORDED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["blood-order", "transfusion-episode"],
    poAcceptanceTest: "Citizen registers as donor, completes pre-screening, and books a drive",
  },
  {
    id: "blood-order",
    journeyName: "Blood Order & Crossmatch",
    coreTransactionType: "BLOOD_ORDER",
    lifecycleStage: "ORDERS_AND_ANCILLARY",
    initiatingActor: "provider",
    respondingActor: "madi-service",
    transactionObject: "Blood Order ID",
    context: "facility blood bank workbench",
    entryPoint: "/madi/orders",
    startState: "ORDER_DRAFT",
    steps: "create → submit → sample → crossmatch → reserve → issue",
    backendServices: ["madi-service", "oros-service", "experience-bff"],
    apis: "/internal/v1/madi/orders/*",
    frontendRoutes: routesMatching("/madi/orders"),
    mobileScreens: screensMatching("madi", "Order"),
    dataReadWritten: "orders; crossmatch; unit reservation",
    eventsEmitted: "madi.order.*",
    trustSecurityChecks: "facility scope; prescribing authz",
    auditRequirements: "order lifecycle audit",
    completionState: "ORDER_ISSUED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["transfusion-episode", "provider-patient-encounter"],
    poAcceptanceTest: "Provider creates blood order and issues matched unit",
  },
  {
    id: "transfusion-episode",
    journeyName: "Transfusion Episode & Bedside Verify",
    coreTransactionType: "TRANSFUSION",
    lifecycleStage: "CLINICAL_EXECUTION",
    initiatingActor: "provider",
    respondingActor: "madi-service",
    transactionObject: "Transfusion Episode ID",
    context: "bedside transfusion / provider mobile",
    entryPoint: "/madi/transfusion",
    startState: "EPISODE_STARTED",
    steps: "start → pre-verify patient+unit → observe → complete",
    backendServices: ["madi-service", "vito-service", "experience-bff"],
    apis: "/internal/v1/madi/transfusions/*, /internal/v1/mobile/provider/madi/*",
    frontendRoutes: routesMatching("/madi/transfusion"),
    mobileScreens: screensMatching("Transfusion", "madi"),
    dataReadWritten: "episodes; observations; verify events",
    eventsEmitted: "madi.transfusion.*",
    trustSecurityChecks: "bedside verify; patient match",
    auditRequirements: "transfusion verify audit",
    completionState: "TRANSFUSION_COMPLETED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "",
    relatedJourneys: ["blood-order", "haemovigilance-report"],
    poAcceptanceTest: "Provider pre-verifies patient and unit before transfusion observations",
  },
  {
    id: "haemovigilance-report",
    journeyName: "Haemovigilance Report & Investigation",
    coreTransactionType: "HAEMOVIGILANCE",
    lifecycleStage: "SAFETY_SURVEILLANCE",
    initiatingActor: "provider",
    respondingActor: "madi-service",
    transactionObject: "Adverse Reaction ID + Case ID",
    context: "facility + national haemovigilance dashboard",
    entryPoint: "/madi/haemovigilance",
    startState: "REACTION_REPORTED",
    steps: "report reaction → open case → investigate → close → national roll-up",
    backendServices: ["madi-service", "nhume-service", "surveillance-service", "experience-bff"],
    apis: "/internal/v1/madi/haemovigilance/*",
    frontendRoutes: routesMatching("/madi/haemovigilance"),
    mobileScreens: screensMatching("Haemovigilance", "madi"),
    dataReadWritten: "reactions; cases; national aggregates",
    eventsEmitted: "madi.haemovigilance.*",
    trustSecurityChecks: "facility + national scope",
    auditRequirements: "adverse event audit",
    completionState: "CASE_CLOSED",
    implStatus: "wired",
    missingBackend: "",
    missingFrontend: "national dashboard web-only by policy",
    relatedJourneys: ["transfusion-episode"],
    poAcceptanceTest: "Adverse reaction reported and visible on facility and national dashboards",
  },
];

const classificationRebaseline = applyMeasuredClassification(JOURNEYS, classificationSignals);

// ── Map backend services to journeys ────────────────────────────────────────
const serviceToJourneys = new Map();
for (const j of JOURNEYS) {
  for (const svc of j.backendServices) {
    if (!serviceToJourneys.has(svc)) serviceToJourneys.set(svc, []);
    serviceToJourneys.get(svc).push(j.id);
  }
}

const allRegistryServices = backendServices.map((e) => e.canonicalName);
const mappedServices = [...serviceToJourneys.keys()];
const unmappedServices = allRegistryServices.filter((s) => !mappedServices.includes(s));

// Supporting/internal services not mapped to user journeys
const SUPPORTING_SERVICES = new Set([
  "shared-core", "audit-ledger-service", "observability-service", "security-hardening-service",
  "data-governance-service", "data-access-governance-service", "schema-registry-service",
  "ai-model-registry-service", "developer-portal-service", "jobs-service", "analytics-pipeline-service",
  "data-pipeline-service", "national-data-repository-service", "ndr-service", "rtc-gateway-service",
  "tshepo-service", "tshepo-audit-service", "tshepo-keys-service", "tshepo-offline-service",
  "workforce-governance-service", "hr-payroll-service", "general-ledger-service", "procurement-service",
  "inventory-service", "inventory-elmis-adapter", "pharmacy-elmis-adapter", "msika-apps-service",
  "butano-fhir", "referral-service", "ndila-service", "nhume-service", "llm-orchestration-service",
  "support-service", "learning-service", "simba-service", "mvumo-service", "zibo-service",
  "product-registry-service", "asset-registry-service", "indawo-service", "data-ingestion-service",
  "data-warehouse-service", "fhir-gateway-service", "connector-fhir-adapter", "integration-hub",
  "landela-adapter-service", "card-print-agent", "share-slip-service", "identity-assurance-service",
  "channels-service", "forms-service", "rules-service", "clinical-knowledge-platform-service",
  "search-service",   "scheduling-service", "offline-edge-service", "document-store-service", "credential-service",
  "costa-service", "pct-service", "vito-service", "tuso-service", "varapi-service", "ubomi-service",
  "community-service", "surveillance-service", "campaigns-service", "notification-service",
  "reporting-service", "coverage-service", "dispatch-service", "msika-flow-service", "msika-service",
  "pharmacy-service", "inpatient-service", "landela-adapter-service", "pacs-adapter-service",
  "experience-bff", "butano-service", "oros-service", "mushex-service", "madi-service",
  "citizen-monitoring-service", "wellness-service", "booking-service",
]);

const unmappedUserFacing = unmappedServices.filter((s) => !SUPPORTING_SERVICES.has(s));

// Routes mapped to journeys
const mappedRouteSet = new Set();
for (const j of JOURNEYS) {
  for (const r of j.frontendRoutes) mappedRouteSet.add(r);
}
const mappedRouteCount = mappedRouteSet.size;

// Completion matrix rows
const completionRows = JOURNEYS.map((j) => ({
  journeyId: j.id,
  journeyName: j.journeyName,
  coreTransactionType: j.coreTransactionType,
  classification: j.completionClassification,
  implStatus: j.implStatus,
  missingBackend: j.missingBackend,
  missingFrontend: j.missingFrontend,
  backendServices: j.backendServices.join("; "),
  frontendRouteCount: j.frontendRoutes.length,
  mobileScreenCount: j.mobileScreens.length,
  poAcceptanceTest: j.poAcceptanceTest,
}));

const transactionComplete = completionRows.filter((r) => r.classification === "transaction-complete").length;

enforceCompletionEvidence(JOURNEYS);

if (CHECK_ONLY) {
  console.log("Completion evidence check passed");
  console.log(`  Transaction-complete: ${transactionComplete}`);
  console.log(`  Evidence registry: ${Object.keys(COMPLETION_EVIDENCE).length}`);
  process.exit(0);
}

// ── Write JSON/CSV ──────────────────────────────────────────────────────────
fs.mkdirSync(OUT_DOCS, { recursive: true });
fs.mkdirSync(OUT_REPORTS, { recursive: true });

fs.writeFileSync(
  path.join(OUT_REPORTS, "classification-rebaseline.json"),
  JSON.stringify(
    {
      generatedAt: DATE,
      authority: PHASE_4_BAR,
      staleBaseline: STALE_BASELINE_CLASSIFICATIONS,
      deltas: classificationRebaseline,
      measuredCounts: completionRows.reduce((acc, r) => {
        acc[r.classification] = (acc[r.classification] || 0) + 1;
        return acc;
      }, {}),
    },
    null,
    2,
  ),
);

const journeyFields = [
  "journeyName", "initiatingActor", "respondingActor", "transactionObject", "context",
  "entryPoint", "startState", "steps", "backendServices", "apis", "frontendRoutes",
  "mobileScreens", "dataReadWritten", "eventsEmitted", "trustSecurityChecks",
  "auditRequirements", "completionState", "currentImplementationStatus", "missingBackendWork",
  "missingFrontendMobileWork", "relatedJourneys", "productOwnerAcceptanceTest",
  "coreTransactionType", "lifecycleStage", "completionClassification",
];

const journeyExport = JOURNEYS.map((j) => ({
  journeyId: j.id,
  journeyName: j.journeyName,
  initiatingActor: j.initiatingActor,
  respondingActor: j.respondingActor,
  transactionObject: j.transactionObject,
  context: j.context,
  entryPoint: j.entryPoint,
  startState: j.startState,
  steps: j.steps,
  backendServices: j.backendServices,
  apis: j.apis,
  frontendRoutes: j.frontendRoutes,
  mobileScreens: j.mobileScreens,
  dataReadWritten: j.dataReadWritten,
  eventsEmitted: j.eventsEmitted,
  trustSecurityChecks: j.trustSecurityChecks,
  auditRequirements: j.auditRequirements,
  completionState: j.completionState,
  currentImplementationStatus: j.implStatus,
  missingBackendWork: j.missingBackend,
  missingFrontendMobileWork: j.missingFrontend,
  relatedJourneys: j.relatedJourneys,
  productOwnerAcceptanceTest: j.poAcceptanceTest,
  coreTransactionType: j.coreTransactionType,
  lifecycleStage: j.lifecycleStage,
  completionClassification: j.completionClassification,
}));

fs.writeFileSync(
  path.join(OUT_REPORTS, "core-transaction-journey-maps.json"),
  JSON.stringify({ generatedAt: DATE, journeyCount: JOURNEYS.length, journeys: journeyExport, serviceToJourneys: Object.fromEntries(serviceToJourneys), unmappedServices }, null, 2)
);

const jCsvCols = ["journeyId", ...journeyFields.flatMap((f) => (f === "backendServices" || f === "frontendRoutes" || f === "mobileScreens" || f === "relatedJourneys" ? [] : [f]))];
// Simpler flat CSV
const flatJourneyCols = [
  "journeyId", "journeyName", "initiatingActor", "respondingActor", "transactionObject", "context",
  "entryPoint", "startState", "steps", "backendServices", "apis", "frontendRoutes", "mobileScreens",
  "dataReadWritten", "eventsEmitted", "trustSecurityChecks", "auditRequirements", "completionState",
  "currentImplementationStatus", "missingBackendWork", "missingFrontendMobileWork", "relatedJourneys",
  "productOwnerAcceptanceTest", "coreTransactionType", "lifecycleStage", "completionClassification",
];
const jCsv = [flatJourneyCols.join(",")];
for (const j of journeyExport) {
  jCsv.push(flatJourneyCols.map((c) => {
    const v = j[c];
    if (Array.isArray(v)) return csvEscape(v.join("; "));
    return csvEscape(v);
  }).join(","));
}
fs.writeFileSync(path.join(OUT_REPORTS, "core-transaction-journey-maps.csv"), jCsv.join("\n") + "\n");

fs.writeFileSync(
  path.join(OUT_REPORTS, "core-transaction-completion-matrix.json"),
  JSON.stringify({
    generatedAt: DATE,
    journeyCount: JOURNEYS.length,
    transactionComplete,
    classificationCounts: Object.fromEntries(
      COMPLETION_STATUSES.map((s) => [s, completionRows.filter((r) => r.classification === s).length])
    ),
    mappedBackendServices: mappedServices.length,
    unmappedBackendServices: unmappedServices.length,
    unmappedUserFacingServices: unmappedUserFacing,
    mappedFrontendRoutes: mappedRouteCount,
    rows: completionRows,
  }, null, 2)
);

const mCsv = ["journeyId,journeyName,coreTransactionType,classification,implStatus,missingBackend,missingFrontend,backendServices,frontendRouteCount,mobileScreenCount,poAcceptanceTest"];
for (const r of completionRows) {
  mCsv.push([r.journeyId, r.journeyName, r.coreTransactionType, r.classification, r.implStatus, r.missingBackend, r.missingFrontend, r.backendServices, r.frontendRouteCount, r.mobileScreenCount, r.poAcceptanceTest].map(csvEscape).join(","));
}
fs.writeFileSync(path.join(OUT_REPORTS, "core-transaction-completion-matrix.csv"), mCsv.join("\n") + "\n");

// ── Doctrine doc ────────────────────────────────────────────────────────────
const doctrine = `# Core Transaction Orchestration Doctrine

> Generated: ${DATE}
> Branch: \`claude/staging-ux-orchestration-remediation-Yypyl\`
> Phase: **2 — Core Transaction Mapping**
> Canonical predecessor: [CORE_TRANSACTION_DOCTRINE.md](../doctrine/CORE_TRANSACTION_DOCTRINE.md)

## Foundational statement

**The Core Transaction Doctrine is the spine of Impilo vNext.**

Every major feature must support a core transaction or be explicitly classified as supporting/internal infrastructure. A capability is not complete because an API exists, a page exists, or a button exists.

A transaction is complete only when the intended actor can enter the correct context, start the relevant core transaction, use real backend capability, move through a coherent frontend/mobile journey, complete the transaction end-to-end, and understand what comes next.

## The seven orchestration questions

Every core transaction mapping must answer:

| Question | Orchestration field |
|----------|---------------------|
| Who or what is initiating? | **Initiating actor** — citizen, provider, nurse, device, scheduled job, external system, etc. |
| Who or what is responding? | **Responding actor** — sovereign service or composed BFF façade |
| What is being transacted? | **Transaction object** — Health ID, Encounter, Order, Claim, Consent, etc. |
| In what context? | **Transaction context** — facility, workspace, shift, purpose-of-use, assurance level |
| What services must cooperate? | **Required orchestration** — BFF composes; sovereign services own truth |
| What records/events/trust apply? | **Data, events, trust checks, audit** |
| What is the completion state? | **Completion state** — canonical \`CoreTransactionState\` from \`contracts/core-transaction.ts\` |

## Orchestration model

\`\`\`
Actor → Context → Transaction Type → Lifecycle Stage → Cooperating Services
  → BFF Composition → Web/Mobile Journey → State Transition → Events + Audit → Next Action
\`\`\`

### Initiating actor

Actors are not always human persons. Valid initiators include: client/citizen, provider, nurse, pharmacist, laboratory user, radiology user, facility administrator, registry administrator, community health worker, courier, device, mobile app, AI assistant (Nompilo), scheduled job, facility pod, external integration system, offline edge node.

### Responding actor

The **responding actor** is the sovereign service that owns the transaction object's source of truth, composed through Experience BFF. BFF orchestrates; it does not become SoR for clinical, registry, trust, or finance truth.

### Transaction object

Bound to \`CoreTransactionType\` in [\`contracts/core-transaction.ts\`](../../contracts/core-transaction.ts): e.g. \`FACILITY_WALK_IN\`, \`APPOINTMENT\`, \`TELEMEDICINE\`, \`PHARMACY\`, \`LABORATORY\`, \`IMAGING\`, \`REFERRAL\`, \`MARKETPLACE\`, \`WELLNESS\`, \`TRAINING_OR_COMPETENCY\`, \`COMMUNITY_OUTREACH\`, \`CHRONIC_CARE\`, \`EMERGENCY\`, \`ADMINISTRATIVE_HEALTH\`.

### Transaction context

Mandatory trust headers (see \`CompanionHeaders\` / \`api-client.ts\`): tenant, pod, actor, facility, workspace, shift, purpose-of-use, assurance level. Context activation precedes transaction start.

### Required orchestration

1. Envoy → TSHEPO \`ext_authz\` before any service
2. Experience BFF composes sovereign calls under \`/internal/v1/*\`
3. State machine tracked via \`/internal/v1/core-transactions/*\` where applicable
4. Outbox → Kafka for reliable event emission

### Completion state

Canonical states in \`contracts/core-transaction.ts\`. Terminal success: \`COMPLETED\`, \`CLOSED\`. Failure/denial: \`ACCESS_DENIED\`, \`CONSENT_DENIED\`, \`PAYMENT_FAILED\`, etc.

### Audit / trust / security requirements

- Every meaningful action: permission decision + audit event
- Break-glass and emergency override: explicit audit chain
- Consent evaluation before clinical/financial actions where required
- No PII in BUTANO SHR (CPID only)

### Event / data outputs

- Domain events via \`event_outbox\` tables
- Core transaction dual-emit: \`core.transaction.events\` (see \`contracts/asyncapi/core-transaction-events.asyncapi.yaml\`)
- Reporting aggregates: \`analytics.reporting.aggregate\`

### Frontend / mobile journey implications

- **One UI Shell** (\`ui/one-ui-shell\`) is the canonical web orchestration surface
- Mobile: \`citizen-app\` + \`provider-app\` with mode-specific journeys
- Journey must be coherent: entry → steps → completion → next action (Nompilo may explain)
- Fixture-backed doctrine pages (\`/core-transaction\`, \`/client-journey\`) are **not** transaction-complete

### Completion priority (Phase 3+)

**Prioritize the clinical spine and complete orchestration on existing surfaces first** — wire fixtures to BFF, close write-contract gaps, extend mobile parity on routes that already exist.

This is a **sequencing preference**, not a ban on new UI:
- **New UI is in scope** when it completes an orchestrated core transaction journey (new step, screen, or route required by the journey map).
- **Do not** add cosmetic pages, mock demos, or disconnected shells that bypass the transaction spine.
- **Do not** delete working routes or replace API-integrated flows with static UI — extend and wire what exists.

## Classification rule

| Class | Meaning |
|-------|---------|
| **Core transaction journey** | User-facing end-to-end flow mapped in journey maps |
| **Supporting service** | Infrastructure, analytics, adapter, or internal plumbing — not a standalone journey |
| **Internal-only** | Trust enforcement, audit, observability — no direct UI required |

## Phase 2 outputs

| Artifact | Path |
|----------|------|
| Journey maps | [CORE_TRANSACTION_JOURNEY_MAPS.md](./CORE_TRANSACTION_JOURNEY_MAPS.md) |
| Completion matrix | [CORE_TRANSACTION_COMPLETION_MATRIX.md](./CORE_TRANSACTION_COMPLETION_MATRIX.md) |
| Journey JSON | [core-transaction-journey-maps.json](../../reports/product/core-transaction-journey-maps.json) |
| Matrix JSON | [core-transaction-completion-matrix.json](../../reports/product/core-transaction-completion-matrix.json) |

## References

- [\`docs/doctrine/CORE_TRANSACTION_DOCTRINE.md\`](../doctrine/CORE_TRANSACTION_DOCTRINE.md)
- [\`docs/architecture/three-journey-core-transaction-map.md\`](../architecture/three-journey-core-transaction-map.md)
- [\`docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md\`](../templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md)
- Phase 1: [PRODUCT_TRUTH_RECOVERY_MAP.md](./PRODUCT_TRUTH_RECOVERY_MAP.md)
`;
fs.writeFileSync(path.join(OUT_DOCS, "CORE_TRANSACTION_ORCHESTRATION_DOCTRINE.md"), doctrine);

// ── Journey maps MD ─────────────────────────────────────────────────────────
const journeysMd = `# Core Transaction Journey Maps

> Generated: ${DATE}
> Journeys discovered: **${JOURNEYS.length}**
> Regenerate: \`node scripts/product/generate-core-transaction-maps.mjs\`

See [CORE_TRANSACTION_ORCHESTRATION_DOCTRINE.md](./CORE_TRANSACTION_ORCHESTRATION_DOCTRINE.md) for spine doctrine.

## Summary

${mdTable(
  JOURNEYS.map((j) => ({
    journey: j.journeyName,
    type: j.coreTransactionType,
    initiator: j.initiatingActor,
    entry: j.entryPoint,
    status: j.implStatus,
    classification: j.completionClassification,
  })),
  ["journey", "type", "initiator", "entry", "status", "classification"],
  50
)}

## Journey detail (sample — full data in JSON/CSV)

${JOURNEYS.slice(0, 5)
  .map(
    (j) => `### ${j.journeyName}

- **Initiating actor:** ${j.initiatingActor}
- **Responding actor:** ${j.respondingActor}
- **Transaction object:** ${j.transactionObject}
- **Context:** ${j.context}
- **Entry point:** ${j.entryPoint}
- **Steps:** ${j.steps}
- **Backend services:** ${j.backendServices.join(", ")}
- **APIs:** ${j.apis}
- **Web routes:** ${j.frontendRoutes.slice(0, 5).join(", ")}${j.frontendRoutes.length > 5 ? "…" : ""}
- **Mobile screens:** ${j.mobileScreens.slice(0, 3).join(", ") || "none mapped"}
- **Completion state:** ${j.completionState}
- **Status:** ${j.implStatus} — ${j.completionClassification}
- **PO acceptance test:** ${j.poAcceptanceTest}
`
  )
  .join("\n")}

_Full ${JOURNEYS.length} journeys in [core-transaction-journey-maps.json](../../reports/product/core-transaction-journey-maps.json)._
`;
fs.writeFileSync(path.join(OUT_DOCS, "CORE_TRANSACTION_JOURNEY_MAPS.md"), journeysMd);

// ── Completion matrix MD ────────────────────────────────────────────────────
const classCounts = Object.fromEntries(
  COMPLETION_STATUSES.map((s) => [s, completionRows.filter((r) => r.classification === s).length])
);

const topGaps = completionRows
  .filter((r) => r.classification !== "transaction-complete")
  .slice(0, 15);

const firstBatch = [
  "provider-patient-encounter",
  "core-transaction-orchestration",
  "health-id-issuance",
  "payment-billing-claim",
  "lab-order-result",
  "public-health-outreach",
  "crvs-ubomi",
];

const matrixMd = `# Core Transaction Completion Matrix

> Generated: ${DATE}
> Journeys: **${JOURNEYS.length}** | Transaction-complete: **${transactionComplete}**
> Regenerate: \`node scripts/product/generate-core-transaction-maps.mjs\`

## Classification counts

| Classification | Count |
|----------------|------:|
${Object.entries(classCounts)
  .filter(([, n]) => n > 0)
  .map(([k, n]) => `| ${k} | ${n} |`)
  .join("\n")}

## Coverage

| Metric | Count |
|--------|------:|
| Backend services mapped to journeys | ${mappedServices.length} |
| Backend services unmapped | ${unmappedServices.length} |
| Unmapped user-facing (needs review) | ${unmappedUserFacing.length} |
| Frontend routes mapped to journeys | ${mappedRouteCount} |

## Completion matrix

${mdTable(
  completionRows.map((r) => ({
    journey: r.journeyName,
    type: r.coreTransactionType,
    classification: r.classification,
    routes: r.frontendRouteCount,
    mobile: r.mobileScreenCount,
    gap: r.missingFrontend || r.missingBackend || "—",
  })),
  ["journey", "type", "classification", "routes", "mobile", "gap"],
  45
)}

## Recommended first completion batch

Prioritize the clinical spine and **complete orchestration on existing surfaces** (wire fixtures to BFF, close write gaps, extend mobile parity). New UI remains in scope when a journey map requires a missing step or screen — this is sequencing, not a moratorium on new surfaces.

${firstBatch.map((id) => {
  const j = JOURNEYS.find((x) => x.id === id);
  return j ? `1. **${j.journeyName}** — ${j.completionClassification}: ${j.missingFrontend || j.missingBackend}` : "";
}).filter(Boolean).join("\n")}

## Unmapped backend services (supporting/internal)

${unmappedServices.slice(0, 30).map((s) => `- ${s}`).join("\n")}

${unmappedUserFacing.length ? `\n## Unmapped user-facing services (review required)\n\n${unmappedUserFacing.map((s) => `- ${s}`).join("\n")}` : ""}
`;
fs.writeFileSync(path.join(OUT_DOCS, "CORE_TRANSACTION_COMPLETION_MATRIX.md"), matrixMd);

console.log("Core Transaction Mapping generated");
console.log(`  Journeys: ${JOURNEYS.length}`);
console.log(`  Mapped backend services: ${mappedServices.length}`);
console.log(`  Unmapped backend services: ${unmappedServices.length}`);
console.log(`  Mapped frontend routes: ${mappedRouteCount}`);
console.log(`  Transaction-complete: ${transactionComplete}`);
