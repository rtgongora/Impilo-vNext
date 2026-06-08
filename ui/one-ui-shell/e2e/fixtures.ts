import { test as base, expect } from "@playwright/test";

const mockUser = {
  id: "e2e-provider-001",
  email: "e2e@impilo.zw",
  displayName: "Dr. E2E Test",
  roles: ["CLINICIAN", "ADMIN"],
  actorType: "PROVIDER" as const,
  providerId: "PRV-E2E-001",
  assuranceLevel: "VERIFIED" as const,
  providerActivated: true,
};

const mockFacility = {
  id: "fac-harare-central",
  name: "Harare Central Hospital",
  code: "HCH-001",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY", "PHARMACY", "LAB", "IMAGING"],
};

export const test = base.extend({
  page: async ({ page, context }, use) => {
    await context.addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);

    await page.addInitScript(
      (data) => {
        sessionStorage.setItem("exp:auth_token", "e2e-mock-token");
        sessionStorage.setItem("exp:auth_user", JSON.stringify(data.user));
        sessionStorage.setItem("exp:facility", JSON.stringify(data.facility));
        sessionStorage.setItem("exp:shift", JSON.stringify({
          id: "shift-001",
          startedAt: new Date().toISOString(),
          workspace: "OPD",
        }));
      },
      { user: mockUser, facility: mockFacility },
    );

    await use(page);
  },
});

export { expect };
