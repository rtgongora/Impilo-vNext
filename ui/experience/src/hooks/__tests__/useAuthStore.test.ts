import { describe, it, expect, beforeEach, vi } from "vitest";
import { useAuthStore, type AuthUser } from "../useAuthStore";
import { useOperationalContextStore } from "../useOperationalContextStore";

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
    useOperationalContextStore.getState().reset();
    sessionStorageMock.clear();
    vi.clearAllMocks();
  });

  it("has no user in initial state", () => {
    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.token).toBeNull();
    expect(state.isAuthenticated).toBe(false);
  });

  it("setAuth updates user, token, and isAuthenticated", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123", "ref-456", "2026-12-31T00:00:00Z");

    const state = useAuthStore.getState();
    expect(state.user).toEqual(testUser);
    expect(state.token).toBe("tok-123");
    expect(state.refreshToken).toBe("ref-456");
    expect(state.expiresAt).toBe("2026-12-31T00:00:00Z");
    expect(state.isAuthenticated).toBe(true);
  });

  it("setAuth persists to sessionStorage", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123", "ref-456", "2026-12-31T00:00:00Z");

    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:auth_token", "tok-123");
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:auth_user", JSON.stringify(testUser));
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:refresh_token", "ref-456");
    expect(sessionStorageMock.setItem).toHaveBeenCalledWith("exp:expires_at", "2026-12-31T00:00:00Z");
  });

  it("setAuth handles optional refreshToken and expiresAt", () => {
    useAuthStore.getState().setAuth(testUser, "tok-123");

    const state = useAuthStore.getState();
    expect(state.user).toEqual(testUser);
    expect(state.token).toBe("tok-123");
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
    vi.clearAllMocks();

    useAuthStore.getState().clearAuth();

    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:auth_token");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:auth_user");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:refresh_token");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:expires_at");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:operational_mode");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:facility_work_subcontext");
    expect(sessionStorageMock.removeItem).toHaveBeenCalledWith("exp:registry_admin_subtype");
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

  it("setTokens updates tokens without changing user", () => {
    useAuthStore.getState().setAuth(testUser, "tok-old", "ref-old");

    useAuthStore.getState().setTokens("tok-new", "ref-new", "2027-01-01T00:00:00Z");

    const state = useAuthStore.getState();
    expect(state.user).toEqual(testUser);
    expect(state.token).toBe("tok-new");
    expect(state.refreshToken).toBe("ref-new");
    expect(state.expiresAt).toBe("2027-01-01T00:00:00Z");
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
