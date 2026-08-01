import { describe, it, expect, beforeEach, vi } from "vitest";
import { useAuthStore, type AuthUser } from "../useAuthStore";
import { useFacilityStore } from "../useFacilityStore";
import { useOperationalContextStore } from "../useOperationalContextStore";
import { useShiftStore } from "../useShiftStore";
import { useWorkModeStore } from "../useWorkModeStore";
import { useWorkspaceStore } from "../useWorkspaceStore";
import { EXPERIENCE_CONTINUITY_SESSION_KEYS } from "@/lib/session-continuity";

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
    removeItem: vi.fn((key: string) => { delete store[key]; }),
    clear: vi.fn(() => { store = {}; }),
    get length() { return Object.keys(store).length; },
    key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
  };
})();

Object.defineProperty(global, "sessionStorage", { value: sessionStorageMock });

const testUser: AuthUser = {
  id: "U-001",
  email: "dr.jones@impilo.health",
  displayName: "Dr. Jones",
  roles: ["PROVIDER", "ADMIN"],
  actorType: "PROVIDER",
  assuranceLevel: "VERIFIED",
  providerActivated: false,
};

describe("useAuthStore", () => {
  beforeEach(() => {
    // Reset store to initial state
    useAuthStore.setState({
      user: null,
      token: null,
      refreshToken: null,
      expiresAt: null,
      isAuthenticated: false,
    });
    useFacilityStore.getState().clearFacility();
    useWorkspaceStore.getState().clearWorkspace();
    useShiftStore.getState().endShift();
    useOperationalContextStore.getState().reset();
    useWorkModeStore.getState().reset();
    sessionStorageMock.clear();
    vi.clearAllMocks();
  });

  it("has no user in initial state", () => {
    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.token).toBeNull();
    expect(state.isAuthenticated).toBe(false);
  });

  it("setAuth updates user but discards browser OAuth tokens", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123", "ref-456", "2026-12-31T00:00:00Z");

    const state = useAuthStore.getState();
    expect(state.user).toEqual(testUser);
    expect(state.token).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.expiresAt).toBe("2026-12-31T00:00:00Z");
    expect(state.isAuthenticated).toBe(true);
  });

  it("setAuth persists to sessionStorage", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123", "ref-456", "2026-12-31T00:00:00Z");

    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:auth_user", JSON.stringify(testUser));
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:expires_at", "2026-12-31T00:00:00Z");
  });

  it("hydrateSession restores an authenticated session without persisting the access token", () => {
    useAuthStore.getState().hydrateSession(testUser, "ref-456", "2026-12-31T00:00:00Z");

    const state = useAuthStore.getState();
    expect(state.user).toEqual(testUser);
    expect(state.token).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.expiresAt).toBe("2026-12-31T00:00:00Z");
    expect(state.isAuthenticated).toBe(true);
    expect(sessionStorageMock.setItem).not.toHaveBeenCalledWith("exp:auth_token", expect.anything());
    expect(sessionStorageMock.setItem).not.toHaveBeenCalledWith("exp:refresh_token", expect.anything());
  });

  it("setAuth handles optional refreshToken and expiresAt", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123");

    const state = useAuthStore.getState();
    expect(state.user).toEqual(testUser);
    expect(state.token).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.expiresAt).toBeNull();
    expect(state.isAuthenticated).toBe(true);
  });

  it("clearAuth resets all state", () => {
    // First set some auth state
    useAuthStore.getState().setAuth(testUser, "tok-123", "ref-456", "2026-12-31T00:00:00Z");
    expect(useAuthStore.getState().isAuthenticated).toBe(true);

    // Now clear
    useAuthStore.getState().clearAuth();

    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.token).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.expiresAt).toBeNull();
    expect(state.isAuthenticated).toBe(false);
  });

  it("clearAuth removes items from sessionStorage", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123", "ref-456", "2026-12-31T00:00:00Z");
    sessionStorageMock.setItem("exp:facility", JSON.stringify({ id: "facility-1" }));
    sessionStorageMock.setItem("exp:workspace", JSON.stringify({ id: "workspace-1" }));
    sessionStorageMock.setItem("exp:shift", JSON.stringify({ id: "shift-1" }));
    sessionStorageMock.setItem("exp:work_mode", "clinical");
    sessionStorageMock.setItem("exp:work_mode_context", JSON.stringify({ licenseNumber: "L-1" }));
    sessionStorageMock.setItem("exp:purpose_of_use", "TREATMENT");
    vi.clearAllMocks();

    useAuthStore.getState().clearAuth();

    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:auth_token");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:auth_user");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:expires_at");
    for (const key of EXPERIENCE_CONTINUITY_SESSION_KEYS) {
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith(key);
    }
  });

  it("clearAuth resets facility, workspace, shift, work mode, and operational context stores", () => {
    useFacilityStore.getState().setFacility({
      id: "facility-1",
      name: "Central Hospital",
      code: "CH",
      facilityType: "Hospital",
      capabilities: ["queue"],
    });
    useWorkspaceStore.getState().setWorkspace({
      id: "workspace-1",
      name: "OPD",
      workspaceType: "CONSULT",
      facilityId: "facility-1",
    });
    useShiftStore.getState().startShift({
      id: "shift-1",
      startedAt: "2026-04-09T08:00:00Z",
      workspaceId: "workspace-1",
      facilityId: "facility-1",
    });
    useOperationalContextStore.getState().setOperationalMode("facility_work");
    useOperationalContextStore.getState().setFacilityWorkSubcontext("triage");
    useWorkModeStore.getState().setMode("clinical", { licenseNumber: "LIC-123" });

    useAuthStore.getState().setAuth(testUser, "tok-123");
    useAuthStore.getState().clearAuth();

    expect(useFacilityStore.getState().facility).toBeNull();
    expect(useWorkspaceStore.getState().workspace).toBeNull();
    expect(useShiftStore.getState().shift).toBeNull();
    expect(useOperationalContextStore.getState().operationalMode).toBe("my_life");
    expect(useOperationalContextStore.getState().facilityWorkSubcontext).toBeNull();
    expect(useWorkModeStore.getState().mode).toBe("general");
    expect(useWorkModeStore.getState().context).toEqual({});
  });

  it("setAuth clears inherited continuity when replacing the signed-in user", () => {
    useFacilityStore.getState().setFacility({
      id: "facility-1",
      name: "Central Hospital",
      code: "CH",
      facilityType: "Hospital",
      capabilities: ["queue"],
    });
    useWorkspaceStore.getState().setWorkspace({
      id: "workspace-1",
      name: "OPD",
      workspaceType: "CONSULT",
      facilityId: "facility-1",
    });
    useShiftStore.getState().startShift({
      id: "shift-1",
      startedAt: "2026-04-09T08:00:00Z",
      workspaceId: "workspace-1",
      facilityId: "facility-1",
    });
    useWorkModeStore.getState().setMode("clinical");
    useAuthStore.getState().setAuth(testUser, "tok-123");

    useAuthStore.getState().setAuth(
      { ...testUser, id: "U-002", email: "dr.moyo@impilo.health" },
      "tok-456",
    );

    expect(useFacilityStore.getState().facility).toBeNull();
    expect(useWorkspaceStore.getState().workspace).toBeNull();
    expect(useShiftStore.getState().shift).toBeNull();
    expect(useWorkModeStore.getState().mode).toBe("general");
  });

  it("hasRole returns true for assigned roles", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123");

    expect(useAuthStore.getState().hasRole("PROVIDER")).toBe(true);
    expect(useAuthStore.getState().hasRole("ADMIN")).toBe(true);
  });

  it("hasRole returns false for unassigned roles", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123");

    expect(useAuthStore.getState().hasRole("SUPER_ADMIN")).toBe(false);
  });

  it("hasRole returns false when no user is set", () => {
    expect(useAuthStore.getState().hasRole("PROVIDER")).toBe(false);
  });

  it("setTokens updates expiry but never retains tokens", () => {
    useAuthStore.getState().setAuth(testUser, "tok-old", "ref-old");

    useAuthStore.getState().setTokens("tok-new", "ref-new", "2027-01-01T00:00:00Z");

    const state = useAuthStore.getState();
    expect(state.user).toEqual(testUser);
    expect(state.token).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.expiresAt).toBe("2027-01-01T00:00:00Z");
  });

  it("setTokens discards OAuth material while updating non-secret expiry metadata", () => {
    useAuthStore.getState().setAuth(testUser, "tok-old", "ref-old");
    vi.clearAllMocks();

    useAuthStore.getState().setTokens("tok-new", "ref-new", "2027-01-01T00:00:00Z");

    expect(sessionStorageMock.setItem).not.toHaveBeenCalledWith("exp:auth_token", expect.anything());
    expect(sessionStorageMock.setItem).not.toHaveBeenCalledWith("exp:refresh_token", expect.anything());
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:expires_at", "2027-01-01T00:00:00Z");
  });

  it("isTokenExpired returns false when no expiresAt", () => {
    expect(useAuthStore.getState().isTokenExpired()).toBe(false);
  });

  it("isTokenExpired returns true for past expiry", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123", null, "2020-01-01T00:00:00Z");
    expect(useAuthStore.getState().isTokenExpired()).toBe(true);
  });

  it("isTokenExpired returns false for far-future expiry", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123", null, "2099-01-01T00:00:00Z");
    expect(useAuthStore.getState().isTokenExpired()).toBe(false);
  });
});
