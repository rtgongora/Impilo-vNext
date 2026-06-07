#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), "../..");
const mapsPath = path.join(root, "scripts/product/generate-core-transaction-maps.mjs");
let content = fs.readFileSync(mapsPath, "utf8");

const additions = {
  "chronic-care": {
    bffEndpoints: ["/internal/v1/care-plans"],
    uiRoutes: ["/ehr/[patientId]/care-plans"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/chronic-care-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CareEmergencyInpatientControllerTest.java",
      "ui/one-ui-shell/src/app/ehr/[patientId]/care-plans/page.test.tsx",
    ],
  },
  "patient-search-selection": {
    bffEndpoints: ["/internal/v1/patients"],
    uiRoutes: ["/queue/search"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/patient-search-selection-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/QueueControllerTest.java",
      "ui/one-ui-shell/src/app/queue/search/page.test.tsx",
    ],
  },
  "facility-context-selection": {
    bffEndpoints: ["/internal/v1/facilities"],
    uiRoutes: ["/facility"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/facility-context-selection-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/FacilityControllerTest.java",
    ],
  },
  "workspace-context-selection": {
    bffEndpoints: ["/internal/v1/workspaces", "/internal/v1/shifts"],
    uiRoutes: ["/workspace", "/shift"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/workspace-context-selection-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/WorkspaceControllerTest.java",
    ],
  },
  "provider-login": {
    bffEndpoints: ["/internal/v1/auth"],
    uiRoutes: ["/auth/login/provider-id"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/provider-login-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionControllerTest.java",
      "ui/one-ui-shell/src/app/auth/login/page.test.tsx",
    ],
  },
  "credential-verification": {
    bffEndpoints: ["/internal/v1/credentials"],
    uiRoutes: ["/verify/credential"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/credential-verification-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CredentialVaultControllerTest.java",
    ],
  },
  "provider-registry-onboarding": {
    bffEndpoints: ["/internal/v1/registry"],
    uiRoutes: ["/registry/providers"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/provider-registry-onboarding-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ProviderActivationControllerTest.java",
    ],
  },
  "registry-administration": {
    bffEndpoints: ["/internal/v1/identity"],
    uiRoutes: ["/registry"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/registry-administration-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/IdentityServicesControllerTest.java",
      "ui/one-ui-shell/src/app/registry/page.test.tsx",
    ],
  },
  "integration-sync-replay": {
    bffEndpoints: ["/internal/v1/integration"],
    uiRoutes: ["/admin/integration-status"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/integration-sync-replay-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/IntegrationHubControllerTest.java",
    ],
  },
  "notification-comms": {
    bffEndpoints: ["/internal/v1/communication"],
    uiRoutes: ["/communication"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/notification-comms-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CommunicationControllerTest.java",
      "ui/one-ui-shell/src/app/communication/page.test.tsx",
    ],
  },
  "reporting-dashboard": {
    bffEndpoints: ["/internal/v1/reports"],
    uiRoutes: ["/reports"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/reporting-dashboard-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ReportJobControllerTest.java",
      "ui/one-ui-shell/src/app/reports/page.test.tsx",
    ],
  },
  "marketplace-order": {
    bffEndpoints: ["/internal/v1/marketplace"],
    uiRoutes: ["/marketplace"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/marketplace-order-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/MarketplaceControllerTest.java",
      "ui/one-ui-shell/src/app/marketplace/page.test.tsx",
    ],
  },
  "dispatch-delivery": {
    bffEndpoints: ["/internal/v1/dispatch"],
    uiRoutes: ["/operations/dispatch"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/dispatch-delivery-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/DispatchControllerTest.java",
    ],
  },
  "offline-clinical-queue": {
    bffEndpoints: ["/internal/v1/mobile/provider/offline"],
    uiRoutes: ["/clinical-tools"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/offline-clinical-queue-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobileOfflineControllerTest.java",
    ],
  },
  "ai-guidance-nompilo": {
    bffEndpoints: ["/internal/v1/guidance"],
    uiRoutes: ["/ask"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/ai-guidance-nompilo-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/GuidanceControllerTest.java",
    ],
  },
  "surveillance-outbreak": {
    bffEndpoints: ["/internal/v1/public-health"],
    uiRoutes: ["/public-health"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/surveillance-outbreak-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PublicHealthControllerTest.java",
    ],
  },
  "fundo-learning": {
    bffEndpoints: ["/internal/v1/learning"],
    uiRoutes: ["/learning"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/fundo-learning-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/LearningControllerTest.java",
      "ui/one-ui-shell/src/hooks/queries/useFundoLms.test.ts",
    ],
  },
  "social-community": {
    bffEndpoints: ["/internal/v1/social"],
    uiRoutes: ["/social"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/social-community-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/SocialControllerTest.java",
    ],
  },
  "public-health-outreach": {
    bffEndpoints: ["/internal/v1/public-health"],
    uiRoutes: ["/public-health"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/public-health-outreach-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PublicHealthControllerTest.java",
      "ui/one-ui-shell/src/hooks/queries/usePublicHealth.test.ts",
    ],
  },
  "crvs-ubomi": {
    bffEndpoints: ["/internal/v1/ubomi"],
    uiRoutes: ["/ubomi"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/crvs-ubomi-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/UbomiControllerTest.java",
    ],
  },
  "citizen-onboarding": {
    bffEndpoints: ["/internal/v1/auth/register"],
    uiRoutes: ["/auth/register"],
    tests: [
      "ui/one-ui-shell/src/lib/__tests__/citizen-onboarding-golden-thread.test.ts",
      "services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionControllerTest.java",
      "ui/one-ui-shell/src/app/auth/register/page.test.tsx",
    ],
  },
};

function formatEntry(id, e) {
  const lines = [
    `  "${id}": {`,
    `    bffEndpoints: [${e.bffEndpoints.map((x) => `"${x}"`).join(", ")}],`,
    `    uiRoutes: [${e.uiRoutes.map((x) => `"${x}"`).join(", ")}],`,
    `    tests: [`,
    ...e.tests.map((t) => `      "${t}",`),
    `    ],`,
    `  },`,
  ];
  return lines.join("\n");
}

const evidenceBlock = content.match(/const COMPLETION_EVIDENCE = \{[\s\S]*?\n\};/)[0];

for (const [id, e] of Object.entries(additions)) {
  if (evidenceBlock.includes(`"${id}":`)) {
    console.log("skip existing", id);
    continue;
  }
  for (const testPath of e.tests) {
    const abs = path.join(root, testPath);
    if (!fs.existsSync(abs)) {
      throw new Error(`Missing evidence test: ${testPath}`);
    }
  }
  const block = formatEntry(id, e);
  content = content.replace(
    /(\n  "payment-billing-claim": \{[\s\S]*?\n  \},\n)(};\n\nconst CHECK_ONLY)/,
    `$1${block}\n$2`,
  );
  console.log("added", id);
}

fs.writeFileSync(mapsPath, content);
