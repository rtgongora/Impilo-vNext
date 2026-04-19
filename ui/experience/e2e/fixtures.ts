import { test as base, expect } from "@playwright/test";

// Mock auth state that bypasses Keycloak for E2E
const mockUser = {
  id: "e2e-provider-001",
  name: "Dr. E2E Test",
  email: "e2e@impilo.zw",
  roles: ["CLINICAL", "ADMIN", "FINANCE", "DISPENSER"],
};

const mockFacility = {
  id: "fac-harare-central",
  name: "Harare Central Hospital",
  code: "HCH-001",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY", "PHARMACY", "LAB", "IMAGING"],
};

export const test = base.extend<{ authenticatedPage: typeof base }>({
  // Inject auth state into browser storage before each test.
  // This is a test harness shortcut, not the production auth persistence model.
  page: async ({ page }, use) => {
    await page.addInitScript((data) => {
      // Seed Zustand persisted stores
      localStorage.setItem("exp:auth", JSON.stringify({ state: { user: data.user, token: "e2e-mock-token" }, version: 0 }));
      localStorage.setItem("exp:facility", JSON.stringify({ state: { facility: data.facility }, version: 0 }));
      localStorage.setItem("exp:shift", JSON.stringify({ state: { shift: { id: "shift-001", startedAt: new Date().toISOString(), workspace: "OPD" } }, version: 0 }));
    }, { user: mockUser, facility: mockFacility });
    await use(page);
  },
});

export { expect };
