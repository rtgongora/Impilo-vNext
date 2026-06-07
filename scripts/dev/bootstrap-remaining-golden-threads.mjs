#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), "../..");
const testDir = path.join(root, "ui/one-ui-shell/src/lib/__tests__");

const journeys = [
  { id: "chronic-care", page: "ui/one-ui-shell/src/app/ehr/[patientId]/care-plans/page.tsx", pageNeedles: ["useCarePlans", "useCreateCarePlan"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/CareEmergencyInpatientController.java", bffNeedles: ["care-plans", "CarePlan"] },
  { id: "patient-search-selection", page: "ui/one-ui-shell/src/app/queue/search/page.tsx", pageNeedles: ["usePatients"], hook: "ui/one-ui-shell/src/hooks/queries/usePatients.ts", hookNeedles: ["/internal/v1/patients"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PatientController.java", bffNeedles: ["/internal/v1/patients"] },
  { id: "facility-context-selection", page: "ui/one-ui-shell/src/app/facility/page.tsx", pageNeedles: ["useFacilities"], hook: "ui/one-ui-shell/src/hooks/queries/useFacilities.ts", hookNeedles: ["/internal/v1/facilities"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/FacilityController.java", bffNeedles: ["/internal/v1/facilities"] },
  { id: "workspace-context-selection", page: "ui/one-ui-shell/src/app/workspace/page.tsx", pageNeedles: ["useShifts"], hook: "ui/one-ui-shell/src/hooks/queries/useShifts.ts", hookNeedles: ["/internal/v1/shifts"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WorkspaceController.java", bffNeedles: ["/internal/v1/workspaces"] },
  { id: "provider-login", page: "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx", pageNeedles: ["provider"], hook: "ui/one-ui-shell/src/hooks/queries/useAuth.ts", hookNeedles: ["/internal/v1/auth"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java", bffNeedles: ["/internal/v1/auth"] },
  { id: "credential-verification", page: "ui/one-ui-shell/src/app/verify/credential/page.tsx", pageNeedles: ["credentialVerifyPublic"], hook: "ui/one-ui-shell/src/lib/credentialVerifyPublic.ts", hookNeedles: ["verify"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/CredentialVaultController.java", bffNeedles: ["/internal/v1/credential"] },
  { id: "provider-registry-onboarding", page: "ui/one-ui-shell/src/app/registry/providers/page.tsx", pageNeedles: ["useRegistry"], hook: "ui/one-ui-shell/src/hooks/queries/useRegistry.ts", hookNeedles: ["/internal/v1/registry"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/RegistryController.java", bffNeedles: ["/internal/v1/registry"] },
  { id: "registry-administration", page: "ui/one-ui-shell/src/app/registry/page.tsx", pageNeedles: ["useIdentityOperations"], hook: "ui/one-ui-shell/src/hooks/queries/useIdentityOperations.ts", hookNeedles: ["/internal/v1/identity"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/IdentityServicesController.java", bffNeedles: ["/internal/v1/identity"] },
  { id: "integration-sync-replay", page: "ui/one-ui-shell/src/app/admin/integration-status/page.tsx", pageNeedles: ["useIntegrationHub"], hook: "ui/one-ui-shell/src/hooks/queries/useIntegrationHub.ts", hookNeedles: ["/internal/v1/integration"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/IntegrationHubController.java", bffNeedles: ["/internal/v1/integration"] },
  { id: "notification-comms", page: "ui/one-ui-shell/src/app/communication/page.tsx", pageNeedles: ["useCommunication"], hook: "ui/one-ui-shell/src/hooks/queries/useCommunication.ts", hookNeedles: ["/internal/v1/communication"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/CommunicationController.java", bffNeedles: ["/internal/v1/communication"] },
  { id: "reporting-dashboard", page: "ui/one-ui-shell/src/app/reports/page.tsx", pageNeedles: ["useReports"], hook: "ui/one-ui-shell/src/hooks/queries/useReports.ts", hookNeedles: ["/internal/v1/reports"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ReportJobController.java", bffNeedles: ["/internal/v1/reports"] },
  { id: "marketplace-order", page: "ui/one-ui-shell/src/app/marketplace/page.tsx", pageNeedles: ["useMarketplace"], hook: "ui/one-ui-shell/src/hooks/queries/useMarketplace.ts", hookNeedles: ["/internal/v1/marketplace"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/MarketplaceController.java", bffNeedles: ["/internal/v1/marketplace"] },
  { id: "dispatch-delivery", page: "ui/one-ui-shell/src/app/operations/dispatch/page.tsx", pageNeedles: ["useDispatchDeliveries"], hook: "ui/one-ui-shell/src/hooks/queries/useDispatchOps.ts", hookNeedles: ["/internal/v1/dispatch"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/DispatchController.java", bffNeedles: ["/internal/v1/dispatch"] },
  { id: "offline-clinical-queue", page: "ui/one-ui-shell/src/app/clinical-tools/page.tsx", pageNeedles: ["offline"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobileOfflineController.java", bffNeedles: ["/internal/v1/mobile/provider/offline"] },
  { id: "ai-guidance-nompilo", page: "ui/one-ui-shell/src/app/ask/page.tsx", pageNeedles: ["useGuidance"], hook: "ui/one-ui-shell/src/hooks/queries/useGuidance.ts", hookNeedles: ["/internal/v1/guidance"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/GuidanceController.java", bffNeedles: ["/internal/v1/guidance"] },
  { id: "surveillance-outbreak", page: "ui/one-ui-shell/src/app/public-health/page.tsx", pageNeedles: ["usePublicHealth"], hook: "ui/one-ui-shell/src/hooks/queries/usePublicHealth.ts", hookNeedles: ["/internal/v1/public-health"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PublicHealthController.java", bffNeedles: ["/internal/v1/public-health"] },
  { id: "fundo-learning", page: "ui/one-ui-shell/src/app/learning/page.tsx", pageNeedles: ["useFundo"], hook: "ui/one-ui-shell/src/hooks/queries/useFundoLms.ts", hookNeedles: ["/internal/v1/learning"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/LearningController.java", bffNeedles: ["/internal/v1/learning"] },
  { id: "social-community", page: "ui/one-ui-shell/src/app/social/page.tsx", pageNeedles: ["useSocialFeed"], hook: "ui/one-ui-shell/src/hooks/queries/useSocial.ts", hookNeedles: ["/internal/v1/social"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/SocialController.java", bffNeedles: ["/internal/v1/social"] },
  { id: "public-health-outreach", page: "ui/one-ui-shell/src/app/public-health/page.tsx", pageNeedles: ["usePublicHealth"], hook: "ui/one-ui-shell/src/hooks/queries/usePublicHealth.ts", hookNeedles: ["/internal/v1/public-health"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PublicHealthController.java", bffNeedles: ["/internal/v1/public-health"] },
  { id: "crvs-ubomi", page: "ui/one-ui-shell/src/app/ubomi/page.tsx", pageNeedles: ["useUbomiRegistry"], hook: "ui/one-ui-shell/src/hooks/queries/useUbomiRegistry.ts", hookNeedles: ["/internal/v1/ubomi"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/UbomiController.java", bffNeedles: ["/internal/v1/ubomi"] },
  { id: "citizen-onboarding", page: "ui/one-ui-shell/src/app/auth/register/page.tsx", pageNeedles: ["apiClient", "register"], bff: "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java", bffNeedles: ["/internal/v1/auth"] },
];

for (const j of journeys) {
  const parts = [
    'import { describe, expect, it } from "vitest";',
    'import { readFileSync } from "node:fs";',
    'import { resolve } from "node:path";',
    "",
    `describe("${j.id} golden thread", () => {`,
    '  const repoRoot = resolve(__dirname, "../../../../..");',
    "",
    '  it("UI route wires bounded-context client", () => {',
    `    const page = readFileSync(resolve(repoRoot, "${j.page}"), "utf8");`,
    ...j.pageNeedles.map((n) => `    expect(page).toContain("${n}");`),
    "  });",
  ];
  if (j.hook) {
    parts.push(
      "",
      '  it("hook calls BFF sovereign proxy", () => {',
      `    const hook = readFileSync(resolve(repoRoot, "${j.hook}"), "utf8");`,
      ...j.hookNeedles.map((n) => `    expect(hook).toContain("${n}");`),
      "  });",
    );
  }
  parts.push(
    "",
    '  it("BFF controller exposes journey endpoints", () => {',
    `    const controller = readFileSync(resolve(repoRoot, "${j.bff}"), "utf8");`,
    ...j.bffNeedles.map((n) => `    expect(controller).toContain("${n}");`),
    "  });",
    "});",
    "",
  );
  const file = path.join(testDir, `${j.id}-golden-thread.test.ts`);
  fs.writeFileSync(file, parts.join("\n"));
  console.log("wrote", path.relative(root, file));
}
