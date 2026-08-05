/**
 * ModeRouter Tests — Verifies mode switching behavior.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";
import type { AppMode } from "../../types";

const mockAppState = {
  mode: "provider" as AppMode,
  isOnline: true,
  facilityId: "facility-1",
  facilityName: "Test Facility",
  workspaceId: null,
  workspaceName: null,
  shiftId: null,
  unreadNotifications: 0,
  pendingSyncCount: 0,
  globalError: null,
  providerTab: "dashboard",
  clinicalToolsInitialTab: null,
  supervisorEntryTab: null,
  setMode: vi.fn(),
  setProviderTab: vi.fn(),
  setClinicalToolsInitialTab: vi.fn(),
  setSupervisorEntryTab: vi.fn(),
  setOnlineStatus: vi.fn(),
  setFacilityContext: vi.fn(),
  setWorkspaceContext: vi.fn(),
  setShiftId: vi.fn(),
  setUnreadNotifications: vi.fn(),
  setPendingSyncCount: vi.fn(),
  setGlobalError: vi.fn(),
  clearContext: vi.fn(),
};

vi.mock("../../stores/appStore", () => ({
  useAppStore: () => mockAppState,
  appStore: {
    subscribe: vi.fn(() => () => {}),
    getState: () => mockAppState,
  },
}));

vi.mock("../../navigation/ProviderTabs", () => ({
  ProviderTabs: () => React.createElement("div", { "data-testid": "provider-tabs" }),
}));
vi.mock("../../navigation/OutreachTabs", () => ({
  OutreachTabs: () => React.createElement("div", { "data-testid": "outreach-tabs" }),
}));
vi.mock("../../navigation/SupervisorTabs", () => ({
  SupervisorTabs: () => React.createElement("div", { "data-testid": "supervisor-tabs" }),
}));
vi.mock("../../navigation/OfflineTabs", () => ({
  OfflineTabs: () => React.createElement("div", { "data-testid": "offline-tabs" }),
}));
vi.mock("../../navigation/CourierTabs", () => ({
  CourierTabs: () => React.createElement("div", { "data-testid": "courier-tabs" }),
}));
vi.mock("../../navigation/ModeSwitcher", () => ({
  ModeSwitcher: () => React.createElement("div", { "data-testid": "mode-switcher" }),
}));

vi.mock("@impilo/mobile-auth", () => ({
  // Realm roles are UPPERCASE — see src/lib/roleGroups.ts. "provider" is not a
  // realm role and never was.
  useAuth: () => ({ user: { sub: "user-1", realm_access: { roles: ["CLINICIAN"] } } }),
}));

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  Button: (props: { title: string; testID?: string; onPress: () => void }) =>
    React.createElement("button", { "data-testid": props.testID, onClick: props.onPress }, props.title),
  };
});

import { ModeRouter } from "../../navigation/ModeRouter";

function renderToString(element: React.ReactElement): string {
  const { renderToStaticMarkup } = require("react-dom/server");
  return renderToStaticMarkup(element);
}

describe("ModeRouter", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAppState.mode = "provider";
  });

  it("renders provider tabs by default", () => {
    const result = renderToString(React.createElement(ModeRouter));
    expect(result).toContain("provider-tabs");
    expect(result).toContain("mode-switcher");
  });

  it("renders outreach tabs when mode is outreach", () => {
    mockAppState.mode = "outreach";
    const result = renderToString(React.createElement(ModeRouter));
    expect(result).toContain("outreach-tabs");
  });

  it("renders supervisor tabs when mode is supervisor", () => {
    mockAppState.mode = "supervisor";
    const result = renderToString(React.createElement(ModeRouter));
    expect(result).toContain("supervisor-tabs");
  });

  it("renders offline tabs when mode is offline", () => {
    mockAppState.mode = "offline";
    const result = renderToString(React.createElement(ModeRouter));
    expect(result).toContain("offline-tabs");
  });

  it("includes mode data attribute", () => {
    mockAppState.mode = "provider";
    const result = renderToString(React.createElement(ModeRouter));
    expect(result).toContain('data-mode="provider"');
  });
});
