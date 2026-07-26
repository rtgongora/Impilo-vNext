/**
 * AuthGuard Tests — Verifies auth guard redirects and loading states.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";

// Mock the auth module
const mockAuthState = {
  isAuthenticated: false,
  isLoading: false,
  hasActiveProvider: vi.fn(() => true),
  user: null,
  session: null,
  error: null,
  initialize: vi.fn(),
  login: vi.fn(),
  handleCallback: vi.fn(),
  logout: vi.fn(),
  getValidToken: vi.fn(),
  setTenantContext: vi.fn(),
  setFacility: vi.fn(),
  setWorkspace: vi.fn(),
  setShift: vi.fn(),
  setPurposeOfUse: vi.fn(),
  setDeviceFingerprint: vi.fn(),
};

vi.mock("@impilo/mobile-auth", () => ({
  useAuth: () => mockAuthState,
  authStore: {
    subscribe: vi.fn(() => () => {}),
    getState: () => mockAuthState,
  },
}));

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  LoadingSpinner: ({ size }: { size: string }) =>
    React.createElement("div", { "data-testid": "loading-spinner", "data-size": size }),
  };
});

vi.mock("../../stores/appStore", () => ({
  useAppStore: () => ({
    facilityId: "facility-1",
    facilityName: "Test Facility",
    workspaceId: "workspace-1",
  }),
  appStore: {
    subscribe: vi.fn(() => () => {}),
    getState: () => ({ facilityId: "facility-1", workspaceId: "workspace-1" }),
  },
}));

vi.mock("../../screens/LoginScreen", () => ({
  LoginScreen: () => React.createElement("div", { "data-testid": "login-screen" }),
}));

vi.mock("../../screens/SelectFacilityScreen", () => ({
  SelectFacilityScreen: () => React.createElement("div", { "data-testid": "select-facility-screen" }),
}));

vi.mock("../../screens/SelectWorkspaceScreen", () => ({
  SelectWorkspaceScreen: () => React.createElement("div", { "data-testid": "select-workspace-screen" }),
}));

vi.mock("../../screens/ProviderActivationScreen", () => ({
  ProviderActivationScreen: () => React.createElement("div", { "data-testid": "provider-activation-screen" }),
}));

import { AuthGuard } from "../../navigation/AuthGuard";

function renderToString(element: React.ReactElement): string {
  const { renderToStaticMarkup } = require("react-dom/server");
  return renderToStaticMarkup(element);
}

describe("AuthGuard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthState.isAuthenticated = false;
    mockAuthState.isLoading = false;
    mockAuthState.hasActiveProvider.mockReturnValue(true);
  });

  it("shows loading spinner when auth is loading", () => {
    mockAuthState.isLoading = true;
    const element = React.createElement(AuthGuard, null, React.createElement("div", null, "Child"));
    const result = renderToString(element);
    expect(result).toContain("auth-loading");
  });

  it("shows login screen when not authenticated", () => {
    mockAuthState.isAuthenticated = false;
    mockAuthState.isLoading = false;
    const element = React.createElement(AuthGuard, null, React.createElement("div", null, "Child"));
    const result = renderToString(element);
    expect(result).toContain("login-screen");
  });

  it("renders children when authenticated and facility set", () => {
    mockAuthState.isAuthenticated = true;
    mockAuthState.isLoading = false;
    const element = React.createElement(AuthGuard, null, React.createElement("div", { "data-testid": "child" }, "Child Content"));
    const result = renderToString(element);
    expect(result).toContain("child");
  });
});
